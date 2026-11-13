(function(){
  'use strict';
  var state={page:0,input:'',analyzing:false,downloading:false,status:'',error:'',settingsMessage:'',progress:null,selectedFormatKey:'',selectedFormatTab:0,video:null,history:[]};
  var expandedGroups={};
  var app=document.getElementById('app');
  var bridge=window.VideoDL||{};
  function esc(value){return String(value==null?'':value).replace(/[&<>"']/g,function(ch){return({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch]);});}
  function icon(name){
    var paths={
      play:'<path d="m8 5 11 7-11 7z"/>',
      paste:'<rect x="8" y="4" width="12" height="16" rx="2"/><path d="M4 8v12a2 2 0 0 0 2 2h10"/>',
      shield:'<path d="M12 22s8-4 8-11V5l-8-3-8 3v6c0 7 8 11 8 11z"/><path d="m9 12 2 2 4-4"/>',
      video:'<rect x="3" y="5" width="18" height="14" rx="3"/><path d="m10 9 5 3-5 3z"/>',
      open:'<path d="M14 3h7v7"/><path d="m10 14 11-11"/><path d="M19 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2h6"/>',
      trash:'<path d="M3 6h18"/><path d="M8 6V4h8v2"/><path d="m19 6-1 14H6L5 6"/><path d="M10 11v5M14 11v5"/>',
      folder:'<path d="M3 7a2 2 0 0 1 2-2h5l2 2h7a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>',
      settings:'<circle cx="12" cy="12" r="3"/><path d="m19.4 15 .1.1a1.7 1.7 0 0 1-2.4 2.4l-.1-.1a1.7 1.7 0 0 0-2.9 1.2v.2a1.7 1.7 0 0 1-3.4 0v-.2a1.7 1.7 0 0 0-2.9-1.2l-.1.1a1.7 1.7 0 1 1-2.4-2.4l.1-.1a1.7 1.7 0 0 0-1.2-2.9H4a1.7 1.7 0 0 1 0-3.4h.2a1.7 1.7 0 0 0 1.2-2.9l-.1-.1a1.7 1.7 0 1 1 2.4-2.4l.1.1a1.7 1.7 0 0 0 2.9-1.2V2a1.7 1.7 0 0 1 3.4 0v.2a1.7 1.7 0 0 0 2.9 1.2l.1-.1a1.7 1.7 0 0 1 2.4 2.4l-.1.1a1.7 1.7 0 0 0 1.2 2.9h.2a1.7 1.7 0 0 1 0 3.4h-.2a1.7 1.7 0 0 0-1.2 2.9z"/>'
    };
    return '<svg viewBox="0 0 24 24" aria-hidden="true">'+(paths[name]||paths.video)+'</svg>';
  }
  function duration(seconds){seconds=Number(seconds||0);if(!seconds)return '';var h=Math.floor(seconds/3600),m=Math.floor((seconds%3600)/60),s=Math.floor(seconds%60);return h?h+':'+String(m).padStart(2,'0')+':'+String(s).padStart(2,'0'):m+':'+String(s).padStart(2,'0');}
  function nav(){document.querySelectorAll('.nav-item').forEach(function(btn){btn.classList.toggle('active',Number(btn.dataset.page)===state.page);});}
  function topbar(){return '<header class="topbar"><div class="brand"><span class="brand-mark">'+icon('play')+'</span><span>VideoDL</span></div><div class="local-chip"><i class="local-dot"></i>本机引擎就绪</div></header>';}
  function progressPanel(){if(!state.downloading&&state.progress===null)return '';var pct=Math.max(0,Math.min(100,Number(state.progress||0)));return '<section class="panel progress-panel"><div class="progress-top"><span>'+esc(state.status||'正在处理…')+'</span><span class="progress-percent">'+Math.round(pct)+'%</span></div><div class="progress-track"><div class="progress-fill" style="width:'+pct+'%"></div></div></section>';}
  function note(){if(state.error)return '<div class="status-note error">'+icon('shield')+'<span>'+esc(state.error)+'</span></div>';if(state.status&&!state.downloading)return '<div class="status-note">'+icon('shield')+'<span>'+esc(state.status)+'</span></div>';return '';}
  function formats(){
    var video=state.video;if(!video)return '';
    var list=state.selectedFormatTab===0?(video.videoFormats||[]):(video.audioFormats||[]);
    var groups={};
    if(state.selectedFormatTab===0){list.forEach(function(item){var key=item.groupKey||item.key;(groups[key]||(groups[key]=[])).push(item);});}
    else{list.forEach(function(item){groups[item.key]=[item];});}
    var html='<div class="section-head"><div><h2>选择下载画质</h2><p>仅显示来源实际提供的格式</p></div><span class="section-count">'+list.length+' 个选项</span></div>';
    html+='<div class="format-tabs"><button class="format-tab '+(state.selectedFormatTab===0?'active':'')+'" data-tab="0">视频 · '+(video.videoFormats||[]).length+'</button><button class="format-tab '+(state.selectedFormatTab===1?'active':'')+'" data-tab="1">音频 · '+(video.audioFormats||[]).length+'</button></div>';
    html+='<div class="panel quality-list">';
    if(!list.length){html+='<div class="empty-state"><div class="empty-icon">'+icon('video')+'</div><h3>暂无可用格式</h3><p>这个来源暂时没有返回'+(state.selectedFormatTab===0?'视频':'音频')+'格式。</p></div>';}
    else{
      Object.keys(groups).forEach(function(key){
        var entries=groups[key],first=entries[0],selected=entries.some(function(f){return f.key===state.selectedFormatKey;});
        var title=state.selectedFormatTab===1?first.label:(first.height?first.height+'P':'视频');
        var detail=state.selectedFormatTab===1?first.technical:(first.width+' × '+first.height+(first.fps>0?' · '+first.fps+' FPS':'')+(first.dynamicRange&&first.dynamicRange.toLowerCase()!=='sdr'?' · '+first.dynamicRange:''));
        var expanded=!!expandedGroups[key];
        html+='<div class="quality-row '+(selected?'selected':'')+'" data-select="'+esc(first.key)+'"><span class="quality-radio"></span><div class="quality-info"><div class="quality-title">'+esc(title)+'</div><div class="quality-sub">'+esc(detail||first.technical||'源站格式')+'</div></div><span class="quality-size">'+esc(first.size)+'</span></div>';
        if(entries.length>1){
          html+='<button class="expand-btn" data-expand="'+esc(key)+'">'+(expanded?'收起编码选项':'其他编码格式 '+(entries.length-1)+' 个')+'</button>';
          if(expanded){html+='<div class="variants">';entries.forEach(function(f){html+='<div class="variant quality-row '+(f.key===state.selectedFormatKey?'selected':'')+'" data-select="'+esc(f.key)+'"><span class="quality-radio"></span><div class="quality-info"><div class="quality-title">'+esc(f.technical||f.label)+'</div><div class="quality-sub">'+esc(f.label)+'</div></div><span class="quality-size">'+esc(f.size)+'</span></div>';});html+='</div>';}
        }
      });
    }
    html+='</div>';
    var chosen=list.find(function(f){return f.key===state.selectedFormatKey;})||list[0];
    if(chosen)html+='<button class="primary-btn download-cta" data-download="'+esc(chosen.key)+'" '+(state.downloading||state.analyzing?'disabled':'')+'>'+(state.downloading?'<span class="spinner"></span>正在下载…':icon('folder')+'下载到手机')+'</button>';
    return html;
  }
  function home(){
    var v=state.video;
    var html=topbar();
    html+='<section class="hero"><div class="eyebrow">YOUR VIDEO, YOUR DEVICE</div><h1>想看的视频，<br>随时带在身边。</h1><p>粘贴视频链接或分享文案，获取来源提供的清晰度并保存到手机。</p></section>';
    html+='<section class="panel input-panel"><div class="input-label"><span>视频链接</span><small>支持分享文案自动识别</small></div><div class="input-wrap"><textarea id="video-input" class="url-input" placeholder="粘贴视频网址，或包含链接的分享内容…">'+esc(state.input)+'</textarea><button class="paste-btn" id="paste">'+icon('paste')+' 粘贴</button></div><button class="primary-btn" id="analyze" '+(state.analyzing||state.downloading?'disabled':'')+'>'+(state.analyzing?'<span class="spinner"></span>正在解析…':icon('play')+'获取视频信息')+'</button><div class="helper-line">'+icon('shield')+'在本机解析，无需填写服务器或 API</div></section>';
    html+=note();
    if(v){
      html+='<div class="section-head"><div><h2>视频信息</h2><p>已从来源读取媒体信息</p></div></div>';
      html+='<section class="panel video-card"><div class="poster">'+(v.thumbnail?'<img src="'+esc(v.thumbnail)+'" alt="视频封面" onerror="this.remove()">':'<div class="poster-fallback">'+icon('video')+'</div>')+'</div><div class="video-meta"><div class="video-title">'+esc(v.title)+'</div><div class="video-sub"><span>'+esc(v.uploader||'未知上传者')+'</span><span>'+esc(duration(v.duration))+'</span></div></div></section>';
      html+=formats();
    }
    html+=progressPanel();
    html+='<p class="legal">请仅下载你拥有版权、已获授权或平台允许下载的内容。</p>';
    return html;
  }
  function downloads(){
    var html=topbar();
    html+='<section class="page-title"><div class="eyebrow">YOUR LIBRARY</div><h1>下载列表</h1><p>已保存到手机的文件都在这里，点击即可打开。</p></section>';
    if(state.downloading)html+='<div class="list-section-label">正在进行</div>'+progressPanel();
    if(!state.history||!state.history.length)html+='<section class="panel empty-state"><div class="empty-icon">'+icon('folder')+'</div><h3>这里还空空的</h3><p>完成一次下载后，文件记录会出现在这里。<br>视频会保存到 Downloads/VideoDL。</p></section>';
    else{
      html+='<div class="list-section-label">已完成 · '+state.history.length+'</div><section class="panel">';
      state.history.forEach(function(item,index){var date=item.savedAt?new Date(Number(item.savedAt)).toLocaleString():'';html+='<div class="download-item"><div class="file-icon">'+icon('video')+'</div><div class="file-meta"><div class="file-title">'+esc(item.title||'未命名视频')+'</div><div class="file-name">'+esc(item.fileName)+'</div><div class="file-date">'+esc(date)+'</div></div><button class="open-file" data-open="'+index+'" aria-label="打开文件">'+icon('open')+'</button></div>';});
      html+='</section><p class="legal">下载记录仅保存在本机。删除记录不会删除已保存的文件。</p>';
    }
    return html;
  }
  function settingRow(name,sub,ico,action,label,danger,disabled){return '<div class="setting-row"><div class="setting-icon">'+icon(ico)+'</div><div class="setting-copy"><div class="setting-title">'+name+'</div><div class="setting-sub">'+sub+'</div></div>'+(action?'<button class="text-action '+(danger?'danger':'')+'" data-action="'+action+'" '+(disabled?'disabled':'')+'>'+label+'</button>':'')+'</div>';}
  function settings(){
    var html=topbar();
    html+='<section class="page-title"><div class="eyebrow">PREFERENCES</div><h1>设置</h1><p>简单、清晰。下载引擎在设备本地运行。</p></section>';
    html+='<section class="settings-group"><div class="settings-label">下载</div><div class="panel">'+settingRow('保存位置','Downloads/VideoDL','folder','','')+'<div class="setting-divider"></div>'+settingRow('下载引擎','本机 Python + yt-dlp，不需要服务端或 API','settings','','')+'</div></section>';
    html+='<section class="settings-group"><div class="settings-label">存储管理</div><div class="panel">'+settingRow('清理临时文件','删除未完成任务的临时缓存，不影响已保存文件','trash','temp','清理',false,state.downloading)+'<div class="setting-divider"></div>'+settingRow('清空下载列表','只清除应用内记录，不会删除手机文件','trash','history','清空',true,false)+'</div></section>';
    html+='<section class="settings-group"><div class="settings-label">关于 VideoDL</div><div class="panel">'+settingRow('VideoDL','版本 0.5.0 · Android ARM64','video','','')+'<div class="setting-divider"></div><div class="about-note">请仅下载你拥有版权、已获授权或平台允许下载的内容。应用不会绕过 DRM、付费墙或访问控制。</div></div></section>';
    if(state.settingsMessage)html+='<div class="message-banner">'+esc(state.settingsMessage)+'</div>';
    return html;
  }
  function bind(){
    var input=document.getElementById('video-input'),analyze=document.getElementById('analyze'),paste=document.getElementById('paste');
    if(input)input.addEventListener('input',function(){bridge.rememberInput(input.value);});
    if(analyze)analyze.addEventListener('click',function(){if(input)bridge.analyze(input.value);});
    if(paste)paste.addEventListener('click',function(){bridge.paste();});
    document.querySelectorAll('[data-tab]').forEach(function(btn){btn.addEventListener('click',function(){var tab=Number(btn.dataset.tab),list=tab===0?(state.video.videoFormats||[]):(state.video.audioFormats||[]);state.selectedFormatTab=tab;state.selectedFormatKey=list.length?list[0].key:'';bridge.selectFormat(state.selectedFormatKey,tab);});});
    document.querySelectorAll('[data-select]').forEach(function(row){row.addEventListener('click',function(){bridge.selectFormat(row.dataset.select,state.selectedFormatTab);});});
    document.querySelectorAll('[data-expand]').forEach(function(btn){btn.addEventListener('click',function(){var k=btn.dataset.expand;expandedGroups[k]=!expandedGroups[k];render();});});
    document.querySelectorAll('[data-download]').forEach(function(btn){btn.addEventListener('click',function(){bridge.download(btn.dataset.download);});});
    document.querySelectorAll('[data-open]').forEach(function(btn){btn.addEventListener('click',function(){bridge.openDownload(Number(btn.dataset.open));});});
    document.querySelectorAll('[data-action]').forEach(function(btn){btn.addEventListener('click',function(){if(btn.dataset.action==='temp')bridge.clearTemp();if(btn.dataset.action==='history'&&window.confirm('清空下载列表记录？已经保存到手机的文件不会删除。'))bridge.clearHistory();});});
  }
  function render(){app.innerHTML=state.page===0?home():(state.page===1?downloads():settings());nav();bind();}
  window.VideoDLRender=function(next){if(!next)return;state=Object.assign({},state,next);render();};
  document.querySelectorAll('.nav-item').forEach(function(btn){btn.addEventListener('click',function(){bridge.navigate(Number(btn.dataset.page));});});
  render();
})();