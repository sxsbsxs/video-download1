(function () {
  'use strict';

  var app = document.getElementById('app');
  var bridge = window.VideoDL || {};
  var state = {
    page: 0, input: '', analyzing: false, downloading: false,
    status: '', error: '', settingsMessage: '', progress: null,
    selectedFormatKey: '', selectedFormatTab: 0, video: null, history: []
  };
  var selectedQualityKey = '';

  function esc(value) {
    return String(value == null ? '' : value).replace(/[&<>"']/g, function (c) {
      return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c];
    });
  }

  function svg(name) {
    var paths = {
      link: '<path d="M10 13a5 5 0 0 0 7.07 0l3-3A5 5 0 0 0 13 2.93l-1.72 1.72"/><path d="M14 11a5 5 0 0 0-7.07 0l-3 3A5 5 0 0 0 11 21.07l1.72-1.72"/>',
      paste: '<rect x="8" y="4" width="12" height="16" rx="2"/><path d="M4 8v12a2 2 0 0 0 2 2h10"/>',
      arrow: '<path d="M5 12h14"/><path d="m12 5 7 7-7 7"/>',
      gear: '<circle cx="12" cy="12" r="3"/><path d="m19.4 15 .1.1a1.7 1.7 0 0 1-2.4 2.4l-.1-.1a1.7 1.7 0 0 0-2.9 1.2v.2a1.7 1.7 0 0 1-3.4 0v-.2a1.7 1.7 0 0 0-2.9-1.2l-.1.1a1.7 1.7 0 1 1-2.4-2.4l.1-.1a1.7 1.7 0 0 0-1.2-2.9H4a1.7 1.7 0 0 1 0-3.4h.2a1.7 1.7 0 0 0 1.2-2.9l-.1-.1a1.7 1.7 0 1 1 2.4-2.4l.1.1a1.7 1.7 0 0 0 2.9-1.2V2a1.7 1.7 0 0 1 3.4 0v.2a1.7 1.7 0 0 0 2.9 1.2l.1-.1a1.7 1.7 0 0 1 2.4 2.4l-.1.1a1.7 1.7 0 0 0 1.2 2.9h.2a1.7 1.7 0 0 1 0 3.4h-.2a1.7 1.7 0 0 0-1.2 2.9z"/>',
      file: '<path d="M13 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z"/><path d="M13 2v7h7"/>',
      video: '<rect x="3" y="5" width="18" height="14" rx="3"/><path d="m10 9 5 3-5 3z"/>',
      audio: '<path d="M9 18V5l12-2v13"/><circle cx="6" cy="18" r="3"/><circle cx="18" cy="16" r="3"/>',
      folder: '<path d="M3 7a2 2 0 0 1 2-2h5l2 2h7a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>',
      open: '<path d="M14 3h7v7"/><path d="m10 14 11-11"/><path d="M19 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2h6"/>',
      trash: '<path d="M3 6h18M8 6V4h8v2m3 0-1 14H6L5 6m5 5v5m4-5v5"/>',
      warn: '<circle cx="12" cy="12" r="9"/><path d="M12 8v5m0 3h.01"/>',
      check: '<path d="m5 12 4 4L19 6"/>',
      back: '<path d="m15 18-6-6 6-6"/><path d="M9 12h11"/>'
    };
    return '<svg viewBox="0 0 24 24" aria-hidden="true">' + (paths[name] || paths.file) + '</svg>';
  }

  function duration(value) {
    var seconds = Number(value || 0);
    if (!seconds) return '';
    var hours = Math.floor(seconds / 3600);
    var minutes = Math.floor((seconds % 3600) / 60);
    var remainder = Math.floor(seconds % 60);
    return hours
      ? hours + ':' + String(minutes).padStart(2,'0') + ':' + String(remainder).padStart(2,'0')
      : minutes + ':' + String(remainder).padStart(2,'0');
  }

  function codec(value) {
    var name = String(value || '').toLowerCase();
    if (!name || name === 'none') return '';
    if (name.indexOf('avc') === 0 || name.indexOf('h264') === 0) return 'H.264';
    if (name.indexOf('hev') === 0 || name.indexOf('h265') === 0 || name.indexOf('hvc') === 0) return 'H.265';
    if (name.indexOf('av01') === 0) return 'AV1';
    if (name.indexOf('vp9') === 0) return 'VP9';
    if (name.indexOf('mp4a') === 0 || name.indexOf('aac') === 0) return 'AAC';
    if (name.indexOf('opus') === 0) return 'Opus';
    return name.toUpperCase().slice(0,12);
  }

  function bitrateText(format) {
    var rate = Number(format.bitrate || 0);
    if (rate <= 0) return '码率未知';
    return rate >= 1000
      ? (rate / 1000).toFixed(1).replace(/\.0$/,'') + ' Mbps'
      : Math.round(rate) + ' kbps';
  }

  function bitrateLabel(format) {
    var codecs = [codec(format.videoCodec),codec(format.audioCodec)].filter(Boolean);
    var labels = [bitrateText(format),codecs.join(' + '),String(format.extension || '').toUpperCase()];
    if (format.size && format.size !== '大小未知') labels.push(format.size);
    return labels.filter(Boolean).join(' · ');
  }

  function qualityGroups(formats) {
    var grouped = {};
    (formats || []).forEach(function (format) {
      var key = format.groupKey || format.key;
      if (!grouped[key]) grouped[key] = [];
      grouped[key].push(format);
    });
    return Object.keys(grouped).map(function (key) {
      var variants = grouped[key].slice().sort(function (a,b) {
        return Number(b.bitrate || 0) - Number(a.bitrate || 0);
      });
      var first = variants[0];
      var title = first.height > 0 ? first.height + 'P' : first.label;
      if (first.fps > 0) title += ' · ' + first.fps + ' FPS';
      if (first.dynamicRange && String(first.dynamicRange).toLowerCase() !== 'sdr') {
        title += ' · ' + first.dynamicRange;
      }
      return {key:key,title:title,variants:variants};
    }).sort(function (a,b) {
      var x=a.variants[0], y=b.variants[0];
      return Number(y.height||0)-Number(x.height||0)
        || Number(y.fps||0)-Number(x.fps||0)
        || Number(y.bitrate||0)-Number(x.bitrate||0);
    });
  }

  function option(value,label,selected) {
    return '<option value="' + esc(value) + '"' + (selected ? ' selected' : '') + '>'
      + esc(label) + '</option>';
  }

  function header() {
    var html = '<header class="topbar">';
    if (state.page === 2) {
      html += '<button class="back-button" data-page="0" aria-label="返回">' + svg('back') + '</button>';
    } else {
      html += '<a class="wordmark" href="#" aria-label="VideoDL"><span class="brand-mark"><i></i><i></i><i></i></span><span>VideoDL</span></a>';
    }
    html += '<nav class="top-tabs" aria-label="主导航">';
    html += '<button class="top-tab' + (state.page === 0 ? ' active' : '') + '" data-page="0">下载</button>';
    html += '<button class="top-tab' + (state.page === 1 ? ' active' : '') + '" data-page="1">文件'
      + (state.history && state.history.length ? '<span class="tab-count">' + state.history.length + '</span>' : '')
      + '</button></nav>';
    html += '<button class="icon-button" data-page="2" aria-label="设置">' + svg('gear') + '</button></header>';
    return html;
  }

  function progressView() {
    if (!state.downloading) return '';
    var percent = Math.max(0,Math.min(99.5,Number(state.progress || 0)));
    var rounded = percent >= 99 ? Math.floor(percent) : Math.floor(percent);
    return '<section class="transfer-state"><div class="transfer-line"><span class="transfer-indicator"></span>'
      + '<span class="transfer-label">' + esc(state.status || '正在处理文件…') + '</span>'
      + '<span class="transfer-percent">' + rounded + '%</span></div>'
      + '<div class="transfer-track"><div class="transfer-fill" style="width:' + percent + '%"></div></div></section>';
  }

  function errorView() {
    if (!state.error) return '';
    return '<div class="error-message">' + svg('warn') + '<span>' + esc(state.error) + '</span></div>';
  }

  function videoSelector(video) {
    var groups = qualityGroups(video.videoFormats || []);
    var selected = groups.find(function(group){return group.key === selectedQualityKey;}) || null;
    var html = '<div class="selector-row">';
    html += '<label class="select-field"><span>清晰度</span><select id="quality-picker">';
    html += option('', '选择清晰度', !selected);
    groups.forEach(function(group) {
      html += option(group.key,group.title,!!selected && selected.key === group.key);
    });
    html += '</select></label>';
    html += '<label class="select-field"><span>码率 / 编码</span><select id="bitrate-picker"'
      + (selected ? '' : ' disabled') + '>';
    html += option('', selected ? '选择码率' : '先选清晰度', !state.selectedFormatKey);
    if (selected) selected.variants.forEach(function(format) {
      html += option(format.key,bitrateLabel(format),format.key === state.selectedFormatKey);
    });
    html += '</select></label></div>';
    if (!groups.length) html += '<div class="empty-note">没有可用的视频格式</div>';
    return html;
  }

  function audioSelector(video) {
    var formats = (video.audioFormats || []).slice().sort(function(a,b) {
      return Number(b.bitrate || 0)-Number(a.bitrate || 0);
    });
    var selected = formats.find(function(format){return format.key === state.selectedFormatKey;});
    var html = '<label class="select-field audio-select"><span>音频码率</span><select id="audio-picker">';
    html += option('', '选择音频码率', !selected);
    formats.forEach(function(format) {
      html += option(format.key,bitrateLabel(format),!!selected && selected.key === format.key);
    });
    html += '</select></label>';
    if (!formats.length) html += '<div class="empty-note">没有可用的独立音频格式</div>';
    return html;
  }

  function formatPanel(video) {
    var videoMode = state.selectedFormatTab === 0;
    var hasVideo = (video.videoFormats || []).length > 0;
    var hasAudio = (video.audioFormats || []).length > 0;
    var html = '<section class="format-section"><div class="format-topline"><h2>下载设置</h2>'
      + '<div class="segmented"><button class="' + (videoMode?'chosen':'') + '" data-tab="0"'
      + (hasVideo?'':' disabled') + '>视频</button><button class="' + (!videoMode?'chosen':'') + '" data-tab="1"'
      + (hasAudio?'':' disabled') + '>音频</button></div></div>';
    html += videoMode ? videoSelector(video) : audioSelector(video);
    html += '</section>';
    return html;
  }

  function videoResult(video) {
    var html = '<section class="video-result">';
    html += '<div class="cover-frame">';
    if (video.thumbnail) html += '<img src="' + esc(video.thumbnail) + '" alt="视频封面">';
    else html += '<div class="cover-fallback">' + svg('video') + '</div>';
    html += '</div><div class="video-description"><h2>' + esc(video.title) + '</h2>';
    var meta = [video.uploader,duration(video.duration)].filter(Boolean);
    html += '<div class="video-meta">' + esc(meta.join(' · ')) + '</div></div></section>';
    return html;
  }

  function homePage() {
    var html = header() + '<section class="home-intro"><h1>下载视频</h1></section>';
    html += '<section class="link-composer"><div class="link-input-wrap">' + svg('link')
      + '<textarea id="video-input" class="url-input" rows="1" placeholder="粘贴视频链接…" '
      + (state.analyzing || state.downloading ? 'disabled' : '') + '>'
      + esc(state.input) + '</textarea><button class="paste-button" id="paste" aria-label="粘贴"'
      + (state.analyzing || state.downloading ? ' disabled' : '') + '>' + svg('paste') + '粘贴</button></div>';
    html += '<button class="parse-button" id="analyze"'
      + (state.analyzing || state.downloading || !state.input.trim() ? ' disabled' : '') + '>'
      + (state.analyzing ? '<span class="spinner"></span>正在解析' : '解析' + svg('arrow'))
      + '</button></section>';
    html += errorView();
    if (state.video) {
      html += videoResult(state.video) + formatPanel(state.video);
      var formatReady = !!state.selectedFormatKey;
      html += '<div class="bottom-action"><div class="save-destination"><span class="destination-dot"></span>'
        + '保存到 <b>Downloads / VideoDL</b></div><button class="download-button" data-download="'
        + esc(state.selectedFormatKey) + '"' + (!formatReady || state.downloading ? ' disabled' : '') + '>'
        + (state.downloading ? '<span class="spinner"></span>正在下载' : '下载到手机' + svg('arrow'))
        + '</button></div>';
    }
    html += progressView();
    return html;
  }

  function filesPage() {
    var html = header() + '<section class="page-title"><h1>已下载</h1></section>';
    if (!state.history || !state.history.length) {
      html += '<div class="empty-library"><div class="empty-art">' + svg('folder') + '</div>'
        + '<div class="empty-title">这里还没有文件</div></div>';
    } else {
      html += '<section class="file-list">';
      state.history.forEach(function(item,index) {
        var date = item.savedAt ? new Date(Number(item.savedAt)).toLocaleString() : '';
        html += '<article class="file-row"><div class="file-type">' + svg('video') + '</div>'
          + '<div class="file-info"><div class="file-name">' + esc(item.title || item.fileName)
          + '</div><div class="file-meta">' + esc(item.fileName) + (date ? ' · ' + esc(date) : '') + '</div></div>'
          + '<button class="file-open" data-open="' + index + '" aria-label="打开文件">' + svg('open') + '</button></article>';
      });
      html += '</section>';
    }
    html += progressView();
    return html;
  }

  function settingsPage() {
    var html = header() + '<section class="page-title"><h1>设置</h1></section>';
    html += '<section class="settings-group"><div class="settings-caption">存储</div>'
      + '<div class="settings-row"><span class="settings-icon">' + svg('folder') + '</span>'
      + '<div><strong>下载目录</strong><small>Downloads / VideoDL</small></div></div>'
      + '<div class="settings-row"><span class="settings-icon">' + svg('trash') + '</span>'
      + '<div><strong>清理临时文件</strong><small>移除未完成下载缓存</small></div>'
      + '<button class="text-action" data-action="temp">清理</button></div></section>';
    html += '<section class="settings-group"><div class="settings-caption">应用</div>'
      + '<div class="settings-row"><span class="settings-icon">' + svg('gear') + '</span>'
      + '<div><strong>下载引擎</strong><small>本机运行 · yt-dlp</small></div></div>'
      + '<div class="settings-row"><span class="settings-icon">' + svg('trash') + '</span>'
      + '<div><strong>清空下载记录</strong><small>不会删除手机里的文件</small></div>'
      + '<button class="text-action danger" data-action="history">清空</button></div></section>';
    if (state.settingsMessage) html += '<div class="settings-message">' + esc(state.settingsMessage) + '</div>';
    return html;
  }

  function bind() {
    document.querySelectorAll('[data-page]').forEach(function(button) {
      button.addEventListener('click',function() {
        bridge.navigate(Number(button.dataset.page));
      });
    });

    var input = document.getElementById('video-input');
    var parseButton = document.getElementById('analyze');
    if (input) input.addEventListener('input',function() {
      state.input = input.value;
      if (parseButton) parseButton.disabled = !input.value.trim() || state.analyzing || state.downloading;
      bridge.rememberInput(input.value);
    });
    if (parseButton) parseButton.addEventListener('click',function() {
      if (input && input.value.trim()) bridge.analyze(input.value);
    });
    var paste = document.getElementById('paste');
    if (paste) paste.addEventListener('click',function(){bridge.paste();});

    document.querySelectorAll('[data-tab]').forEach(function(button) {
      button.addEventListener('click',function() {
        selectedQualityKey = '';
        state.selectedFormatKey = '';
        state.selectedFormatTab = Number(button.dataset.tab);
        bridge.selectFormat('',state.selectedFormatTab);
      });
    });
    var quality = document.getElementById('quality-picker');
    if (quality) quality.addEventListener('change',function() {
      selectedQualityKey = quality.value;
      state.selectedFormatKey = '';
      bridge.selectFormat('',0);
    });
    var bitrate = document.getElementById('bitrate-picker');
    if (bitrate) bitrate.addEventListener('change',function() {
      state.selectedFormatKey = bitrate.value;
      bridge.selectFormat(bitrate.value,0);
    });
    var audio = document.getElementById('audio-picker');
    if (audio) audio.addEventListener('change',function() {
      state.selectedFormatKey = audio.value;
      bridge.selectFormat(audio.value,1);
    });

    document.querySelectorAll('[data-download]').forEach(function(button) {
      button.addEventListener('click',function() {
        if (button.dataset.download) bridge.download(button.dataset.download);
      });
    });
    document.querySelectorAll('[data-open]').forEach(function(button) {
      button.addEventListener('click',function() {
        bridge.openDownload(Number(button.dataset.open));
      });
    });
    document.querySelectorAll('[data-action]').forEach(function(button) {
      button.addEventListener('click',function() {
        if (button.dataset.action === 'temp') bridge.clearTemp();
        if (button.dataset.action === 'history' && window.confirm('清空下载记录？手机中的文件不会删除。')) {
          bridge.clearHistory();
        }
      });
    });
  }

  function render() {
    app.innerHTML = state.page === 0 ? homePage() : (state.page === 1 ? filesPage() : settingsPage());
    bind();
  }

  window.VideoDLRender = function(next) {
    if (!next) return;
    var oldUrl = state.video && state.video.sourceUrl;
    var newUrl = next.video && next.video.sourceUrl;
    if (newUrl && newUrl !== oldUrl) selectedQualityKey = '';
    if (next.video === null) selectedQualityKey = '';
    state = Object.assign({},state,next);
    render();
  };

  render();
})();