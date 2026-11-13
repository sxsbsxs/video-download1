(function () {
  'use strict';
  var state = {page:0,input:'',analyzing:false,downloading:false,status:'',error:'',settingsMessage:'',progress:null,selectedFormatKey:'',selectedFormatTab:0,video:null,history:[]};
  var selectedQualityKey = '';
  var app = document.getElementById('app');
  var bridge = window.VideoDL || {};

  function esc(value) {
    return String(value == null ? '' : value).replace(/[&<>"']/g, function (c) {
      return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c];
    });
  }
  function icon(name) {
    var paths = {
      play:'<path d="m8 5 11 7-11 7z"/>',
      paste:'<rect x="8" y="4" width="12" height="16" rx="2"/><path d="M4 8v12a2 2 0 0 0 2 2h10"/>',
      warn:'<circle cx="12" cy="12" r="9"/><path d="M12 8v5m0 3h.01"/>',
      video:'<rect x="3" y="5" width="18" height="14" rx="3"/><path d="m10 9 5 3-5 3z"/>',
      folder:'<path d="M3 7a2 2 0 0 1 2-2h5l2 2h7a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>',
      open:'<path d="M14 3h7v7"/><path d="m10 14 11-11"/><path d="M19 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2h6"/>',
      trash:'<path d="M3 6h18M8 6V4h8v2m3 0-1 14H6L5 6m5 5v5m4-5v5"/>',
      settings:'<circle cx="12" cy="12" r="3"/><path d="m19.4 15 .1.1a1.7 1.7 0 0 1-2.4 2.4l-.1-.1a1.7 1.7 0 0 0-2.9 1.2v.2a1.7 1.7 0 0 1-3.4 0v-.2a1.7 1.7 0 0 0-2.9-1.2l-.1.1a1.7 1.7 0 1 1-2.4-2.4l.1-.1a1.7 1.7 0 0 0-1.2-2.9H4a1.7 1.7 0 0 1 0-3.4h.2a1.7 1.7 0 0 0 1.2-2.9l-.1-.1a1.7 1.7 0 1 1 2.4-2.4l.1.1a1.7 1.7 0 0 0 2.9-1.2V2a1.7 1.7 0 0 1 3.4 0v.2a1.7 1.7 0 0 0 2.9 1.2l.1-.1a1.7 1.7 0 0 1 2.4 2.4l-.1.1a1.7 1.7 0 0 0 1.2 2.9h.2a1.7 1.7 0 0 1 0 3.4h-.2a1.7 1.7 0 0 0-1.2 2.9z"/>'
    };
    return '<svg viewBox="0 0 24 24" aria-hidden="true">' + (paths[name] || paths.video) + '</svg>';
  }
  function duration(value) {
    var s = Number(value || 0);
    if (!s) return '';
    var h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), r = Math.floor(s % 60);
    return h ? h + ':' + String(m).padStart(2,'0') + ':' + String(r).padStart(2,'0')
      : m + ':' + String(r).padStart(2,'0');
  }
  function codec(value) {
    var c = String(value || '').toLowerCase();
    if (!c || c === 'none') return '';
    if (c.indexOf('avc') === 0 || c.indexOf('h264') === 0) return 'H.264';
    if (c.indexOf('hev') === 0 || c.indexOf('h265') === 0 || c.indexOf('hvc') === 0) return 'H.265';
    if (c.indexOf('av01') === 0) return 'AV1';
    if (c.indexOf('vp9') === 0) return 'VP9';
    if (c.indexOf('mp4a') === 0 || c.indexOf('aac') === 0) return 'AAC';
    if (c.indexOf('opus') === 0) return 'Opus';
    return c.toUpperCase().slice(0,12);
  }
  function bitrateText(f) {
    var b = Number(f.bitrate || 0);
    if (b <= 0) return '码率未提供';
    return b >= 1000 ? (b / 1000).toFixed(1).replace(/\.0$/,'') + ' Mbps' : Math.round(b) + ' kbps';
  }
  function bitrateLabel(f) {
    var codecs = [codec(f.videoCodec),codec(f.audioCodec)].filter(Boolean);
    var bits = [bitrateText(f),codecs.join(' + '),String(f.extension || '').toUpperCase()];
    if (f.size && f.size !== '大小未知') bits.push(f.size);
    return bits.filter(Boolean).join(' · ');
  }
  function groupsOf(formats) {
    var groups = {};
    (formats || []).forEach(function(f) {
      var key = f.groupKey || f.key;
      if (!groups[key]) groups[key] = [];
      groups[key].push(f);
    });
    return Object.keys(groups).map(function(key) {
      var list = groups[key].slice().sort(function(a,b) {
        return Number(b.bitrate || 0) - Number(a.bitrate || 0);
      });
      var f = list[0];
      var label = f.height > 0 ? f.height + 'P' : f.label;
      if (f.fps > 0) label += ' · ' + f.fps + ' FPS';
      if (f.dynamicRange && String(f.dynamicRange).toLowerCase() !== 'sdr') label += ' · ' + f.dynamicRange;
      return {key:key,label:label,items:list};
    }).sort(function(a,b) {
      var x=a.items[0], y=b.items[0];
      return Number(y.height||0)-Number(x.height||0)
        || Number(y.fps||0)-Number(x.fps||0)
        || Number(y.bitrate||0)-Number(x.bitrate||0);
    });
  }
  function option(value,label,selected) {
    return '<option value="' + esc(value) + '"' + (selected ? ' selected' : '') + '>' + esc(label) + '</option>';
  }
  function top() {
    return '<header class="top"><div class="brand"><span class="brand-symbol">' + icon('play')
      + '</span>VideoDL</div><span class="engine-dot" title="本机引擎"></span></header>';
  }
  function errorView() {
    return state.error ? '<div class="error">' + icon('warn') + '<span>' + esc(state.error) + '</span></div>' : '';
  }
  function progressView() {
    if (!state.downloading && state.progress === null) return '';
    var p = Math.max(0,Math.min(100,Number(state.progress || 0)));
    return '<div class="progress"><div class="progress-top"><span>' + esc(state.status || '处理中')
      + '</span><span class="progress-number">' + Math.round(p) + '%</span></div>'
      + '<div class="track"><div class="fill" style="width:' + p + '%"></div></div></div>';
  }
  function videoSelector(video) {
    var groups = groupsOf(video.videoFormats || []);
    var selectedGroup = groups.find(function(g){return g.key === selectedQualityKey;}) || null;
    var html = '<div class="quality-heading">下载选项</div><div class="selection-grid">';
    html += '<label class="field"><span>清晰度</span><select id="quality-picker">';
    html += option('', '选择清晰度', !selectedGroup);
    groups.forEach(function(g){html += option(g.key,g.label,!!selectedGroup && selectedGroup.key === g.key);});
    html += '</select></label>';
    html += '<label class="field"><span>码率 / 编码</span><select id="bitrate-picker"'
      + (selectedGroup ? '' : ' disabled') + '>';
    html += option('',selectedGroup ? '选择码率' : '先选择清晰度',!state.selectedFormatKey);
    if (selectedGroup) selectedGroup.items.forEach(function(f) {
      html += option(f.key,bitrateLabel(f),f.key === state.selectedFormatKey);
    });
    html += '</select></label></div>';
    if (!groups.length) html += '<div class="empty-inline">这个来源没有返回可用的视频格式。</div>';
    return html;
  }
  function audioSelector(video) {
    var list = (video.audioFormats || []).slice().sort(function(a,b){
      return Number(b.bitrate || 0) - Number(a.bitrate || 0);
    });
    var selected = list.find(function(f){return f.key === state.selectedFormatKey;});
    var html = '<div class="quality-heading">音频质量</div><label class="field audio-field">'
      + '<span>音频码率</span><select id="audio-picker">';
    html += option('','选择音频码率',!selected);
    list.forEach(function(f){html += option(f.key,bitrateLabel(f),!!selected && selected.key === f.key);});
    html += '</select></label>';
    if (!list.length) html += '<div class="empty-inline">这个来源没有返回独立音频格式。</div>';
    return html;
  }
  function formatSelector(video) {
    var videoMode = state.selectedFormatTab === 0;
    var hasVideo = (video.videoFormats || []).length > 0;
    var hasAudio = (video.audioFormats || []).length > 0;
    var html = '<section class="format-panel"><div class="mode-switch">'
      + '<button class="mode' + (videoMode ? ' active' : '') + '" data-tab="0"' + (hasVideo ? '' : ' disabled') + '>视频</button>'
      + '<button class="mode' + (!videoMode ? ' active' : '') + '" data-tab="1"' + (hasAudio ? '' : ' disabled') + '>音频</button>'
      + '</div>';
    html += videoMode ? videoSelector(video) : audioSelector(video);
    var ready = !!state.selectedFormatKey && !state.downloading && !state.analyzing;
    var text = state.downloading ? '<span class="spinner"></span>正在下载'
      : (state.selectedFormatKey ? '开始下载' : (videoMode ? '选择清晰度和码率' : '选择音频码率'));
    html += '<button class="download-action" data-download="' + esc(state.selectedFormatKey) + '"'
      + (ready ? '' : ' disabled') + '>' + text + '</button></section>';
    return html;
  }
  function home() {
    var v = state.video;
    var html = top() + '<section class="heading"><h1>下载视频</h1></section>';
    html += '<section class="composer"><div class="input-line"><textarea id="video-input" class="url-input"'
      + ' placeholder="粘贴视频链接…" ' + (state.downloading || state.analyzing ? 'disabled' : '') + '>'
      + esc(state.input) + '</textarea><button id="paste" class="paste"'
      + (state.downloading || state.analyzing ? ' disabled' : '') + '>' + icon('paste') + '粘贴</button></div>';
    html += '<button id="analyze" class="analyze"' + (state.analyzing || state.downloading || !state.input.trim() ? ' disabled' : '') + '>'
      + (state.analyzing ? '<span class="spinner"></span>正在解析' : '解析') + '</button></section>';
    html += errorView();
    if (v) {
      html += '<section class="video-result"><div class="thumb">';
      if (v.thumbnail) html += '<img src="' + esc(v.thumbnail) + '" alt="视频封面">';
      else html += '<span class="thumb-fallback">' + icon('video') + '</span>';
      html += '</div><div class="video-copy"><div class="video-title">' + esc(v.title)
        + '</div><div class="video-extra">' + esc(v.uploader || '')
        + (v.duration ? ' · ' + duration(v.duration) : '') + '</div></div></section>';
      html += formatSelector(v);
    }
    return html + progressView();
  }
  function downloads() {
    var html = top() + '<header class="page-head"><h1>下载</h1></header>';
    if (state.downloading) html += progressView();
    if (!state.history || !state.history.length) {
      html += '<section class="history"><div class="empty"><div class="empty-mark">' + icon('folder')
        + '</div><strong>暂无下载</strong>完成的文件会显示在这里</div></section>';
    } else {
      html += '<section class="history">';
      state.history.forEach(function(item,i) {
        var date = item.savedAt ? new Date(Number(item.savedAt)).toLocaleString() : '';
        html += '<div class="download-item"><div class="file-symbol">' + icon('video') + '</div>'
          + '<div class="file-copy"><div class="file-title">' + esc(item.title || item.fileName)
          + '</div><div class="file-detail">' + esc(item.fileName) + ' · ' + esc(date)
          + '</div></div><button class="open-file" data-open="' + i + '" aria-label="打开">'
          + icon('open') + '</button></div>';
      });
      html += '</section>';
    }
    return html;
  }
  function settings() {
    var html = top() + '<header class="page-head"><h1>设置</h1></header>';
    html += '<div class="minor-label">存储</div><section class="settings-list">'
      + '<div class="setting"><span class="setting-icon">' + icon('folder') + '</span><div class="setting-copy">'
      + '<div class="setting-name">保存位置</div><div class="setting-value">Downloads / VideoDL</div></div></div>'
      + '<div class="setting"><span class="setting-icon">' + icon('trash') + '</span><div class="setting-copy">'
      + '<div class="setting-name">清理临时文件</div><div class="setting-value">删除未完成下载缓存</div></div>'
      + '<button class="setting-action" data-action="temp">清理</button></div></section>';
    html += '<div class="minor-label">应用</div><section class="settings-list">'
      + '<div class="setting"><span class="setting-icon">' + icon('settings') + '</span><div class="setting-copy">'
      + '<div class="setting-name">下载引擎</div><div class="setting-value">本机 Python · yt-dlp</div></div></div>'
      + '<div class="setting"><span class="setting-icon">' + icon('trash') + '</span><div class="setting-copy">'
      + '<div class="setting-name">清空下载记录</div><div class="setting-value">不删除手机中的文件</div></div>'
      + '<button class="setting-action danger" data-action="history">清空</button></div></section>';
    if (state.settingsMessage) html += '<div class="message">' + esc(state.settingsMessage) + '</div>';
    return html;
  }
  function bind() {
    var input = document.getElementById('video-input');
    var analyzeButton = document.getElementById('analyze');
    var pasteButton = document.getElementById('paste');
    if (input) input.addEventListener('input',function(){
      state.input = input.value;
      if (analyzeButton) analyzeButton.disabled = !input.value.trim() || state.analyzing || state.downloading;
      bridge.rememberInput(input.value);
    });
    if (analyzeButton) analyzeButton.addEventListener('click',function(){if(input)bridge.analyze(input.value);});
    if (pasteButton) pasteButton.addEventListener('click',function(){bridge.paste();});
    document.querySelectorAll('[data-tab]').forEach(function(button){
      button.addEventListener('click',function(){
        selectedQualityKey = '';
        state.selectedFormatKey = '';
        state.selectedFormatTab = Number(button.dataset.tab);
        bridge.selectFormat('',state.selectedFormatTab);
      });
    });
    var quality = document.getElementById('quality-picker');
    if (quality) quality.addEventListener('change',function(){
      selectedQualityKey = quality.value;
      state.selectedFormatKey = '';
      bridge.selectFormat('',0);
    });
    var bitrate = document.getElementById('bitrate-picker');
    if (bitrate) bitrate.addEventListener('change',function(){
      state.selectedFormatKey = bitrate.value;
      bridge.selectFormat(bitrate.value,0);
    });
    var audio = document.getElementById('audio-picker');
    if (audio) audio.addEventListener('change',function(){
      state.selectedFormatKey = audio.value;
      bridge.selectFormat(audio.value,1);
    });
    document.querySelectorAll('[data-download]').forEach(function(button){
      button.addEventListener('click',function(){if(button.dataset.download)bridge.download(button.dataset.download);});
    });
    document.querySelectorAll('[data-open]').forEach(function(button){
      button.addEventListener('click',function(){bridge.openDownload(Number(button.dataset.open));});
    });
    document.querySelectorAll('[data-action]').forEach(function(button){
      button.addEventListener('click',function(){
        if(button.dataset.action === 'temp') bridge.clearTemp();
        if(button.dataset.action === 'history' && window.confirm('清空下载记录？手机里的文件不会删除。')) bridge.clearHistory();
      });
    });
  }
  function render() {
    app.innerHTML = state.page === 0 ? home() : (state.page === 1 ? downloads() : settings());
    document.querySelectorAll('.nav-item').forEach(function(button){
      button.classList.toggle('active',Number(button.dataset.page) === state.page);
    });
    bind();
  }
  window.VideoDLRender = function(next) {
    if (!next) return;
    var oldSource = state.video && state.video.sourceUrl;
    var newSource = next.video && next.video.sourceUrl;
    if (newSource && newSource !== oldSource) selectedQualityKey = '';
    if (next.video === null) selectedQualityKey = '';
    state = Object.assign({},state,next);
    render();
  };
  document.querySelectorAll('.nav-item').forEach(function(button){
    button.addEventListener('click',function(){bridge.navigate(Number(button.dataset.page));});
  });
  render();
})();