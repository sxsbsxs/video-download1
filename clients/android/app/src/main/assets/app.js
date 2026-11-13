(function(){
'use strict';
var state={page:0,input:'',analyzing:false,downloading:false,status:'',error:'',settingsMessage:'',progress:null,selectedFormatKey:'',selectedFormatTab:0,video:null,history:[]};
var expanded={};
var app=document.getElementById('app');
var bridge=window.VideoDL||{};
function esc(v){return String(v==null?'':v).replace(/[&<>"']/g,function(c){return({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]);});}
function ico(name){
var p={
play:'<path d="m8 5 11 7-11 7z"/>',paste:'<rect x="8" y="4" width="12" height="16" rx="2"/><path d="M4 8v12a2 2 0 0 0 2 2h10"/>',
warn:'<circle cx="12" cy="12" r="9"/><path d="M12 8v5m0 3h.01"/>',video:'<rect x="3" y="5" width="18" height="14" rx="3"/><path d="m10 9 5 3-5 3z"/>',
folder:'<path d="M3 7a2 2 0 0 1 2-2h5l2 2h7a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>',
open:'<path d="M14 3h7v7"/><path d="m10 14 11-11"/><path d="M19 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2h6"/>',
trash:'<path d="M3 6h18M8 6V4h8v2m3 0-1 14H6L5 6m5 5v5m4-5v5"/>',
settings:'<circle cx="12" cy="12" r="3"/><path d="m19.4 15 .1.1a1.7 1.7 0 0 1-2.4 2.4l-.1-.1a1.7 1.7 0 0 0-2.9 1.2v.2a1.7 1.7 0 0 1-3.4 0v-.2a1.7 1.7 0 0 0-2.9-1.2l-.1.1a1.7 1.7 0 1 1-2.4-2.4l.1-.1a1.7 1.7 0 0 0-1.2-2.9H4a1.7 1.7 0 0 1 0-3.4h.2a1.7 1.7 0 0 0 1.2-2.9l-.1-.1a1.7 1.7 0 1 1 2.4-2.4l.1.1a1.7 1.7 0 0 0 2.9-1.2V2a1.7 1.7 0 0 1 3.4 0v.2a1.7 1.7 0 0 0 2.9 1.2l.1-.1a1.7 1.7 0 0 1 2.4 2.4l-.1.1a1.7 1.7 0 0 0 1.2 2.9h.2a1.7 1.7 0 0 1 0 3.4h-.2a1.7 1.7 0 0 0-1.2 2.9z"/>'
};
return '<svg viewBox="0 0 24 24" aria-hidden="true">'+(p[name]||p.video)+'</svg>';
}
function duration(v){v=Number(v||0);if(!v)return '';var h=Math.floor(v/3600),m=Math.floor(v%3600/60),s=Math.floor(v%60);return h?h+':'+String(m).padStart(2,'0')+':'+String(s).padStart(2,'0'):m+':'+String(s).padStart(2,'0');}
function top(){return '<header class="top"><div class="brand"><span class="brand-symbol">'+ico('play')+'</span>VideoDL</div><span class="engine-dot" title="本机引擎"></span></header>';}
function nav(){document.querySelectorAll('.nav-item').forEach(function(n){n.classList.toggle('active',Number(n.dataset.page)===state.page);});}
function progress(){
if(!state.downloading&&state.progress===null)return '';
var n=Math.max(0,Math.min(100,Number(state.progress||0)));
return '<div class="progress"><div class="progress-top"><span>'+esc(state.status||'处理中')+'</span><span class="progress-number">'+Math.round(n)+'%</span></div><div class="track"><div class="fill" style="width:'+n+'%"></div></div></div>';
}
function errorNote(){return state.error?'<div class="error">'+ico('warn')+'<span>'+esc(state.error)+'</span></div>':'';}
function formatArea(){
var v=state.video;if(!v)return '';
var list=state.selectedFormatTab===0?(v.videoFormats||[]):(v.audioFormats||[]);
var groups={};
list.forEach(function(f){var k=state.selectedFormatTab===0?(f.groupKey||f.key):f.key;(groups[k]||(groups[k]=[])).push(f);});
var html='<div class="quality-head"><strong>画质</strong><span class="quality-count">'+list.length+' 个来源选项</span></div>';
html+='<div class="mode-switch"><button class="mode '+(state.selectedFormatTab===0?'active':'')+'" data-tab="0">视频</button><button class="mode '+(state.selectedFormatTab===1?'active':'')+'" data-tab="1">音频</button></div>';
html+='<div class="quality-list">';
if(!list.length)html+='<div class="empty">没有可用格式</div>';
Object.keys(groups).forEach(function(k){
var rows=groups[k],f=rows[0],selected=rows.some(function(x){return x.key===state.selectedFormatKey;});
var name=state.selectedFormatTab===0?(f.height?f.height+'P':f.label):f.label;
var sub=state.selectedFormatTab===0?[f.width&&f.height?f.width+' × '+f.height:'',f.fps>0?f.fps+' FPS':'',f.dynamicRange&&f.dynamicRange.toLowerCase()!=='sdr'?f.dynamicRange:''].filter(Boolean).join(' · '):(f.technical||f.extension.toUpperCase());
var isOpen=!!expanded[k];
html+='<div class="quality-row '+(selected?'selected':'')+'" data-select="'+esc(f.key)+'"><span class="radio"></span><div class="q-copy"><div class="q-main">'+esc(name)+'</div>'+(sub?'<div class="q-sub">'+esc(sub)+'</div>':'')+'</div><span class="q-size">'+esc(f.size)+'</span></div>';
if(rows.length>1){
html+='<button class="more-formats" data-expand="'+esc(k)+'">'+(isOpen?'收起':'其他编码 · '+(rows.length-1))+'</button>';
if(isOpen){html+='<div class="variants">';rows.forEach(function(x){html+='<div class="quality-row variant '+(x.key===state.selectedFormatKey?'selected':'')+'" data-select="'+esc(x.key)+'"><span class="radio"></span><div class="q-copy"><div class="q-main">'+esc(x.technical||x.label)+'</div><div class="q-sub">'+esc(x.label)+'</div></div><span class="q-size">'+esc(x.size)+'</span></div>';});html+='</div>';}
}
});
html+='</div>';
var chosen=list.find(function(f){return f.key===state.selectedFormatKey;})||list[0];
if(chosen)html+='<button class="download-action" data-download="'+esc(chosen.key)+'" '+(state.downloading||state.analyzing?'disabled':'')+'>'+ico('folder')+'下载到手机</button>';
return html;
}
function home(){
var v=state.video;
var out=top()+'<section class="heading"><h1>下载视频</h1></section>';
out+='<section class="composer"><div class="input-line"><textarea id="video-input" class="url-input" placeholder="粘贴视频链接…">'+esc(state.input)+'</textarea><button id="paste" class="paste">'+ico('paste')+' 粘贴</button></div><button id="analyze" class="analyze" '+(state.analyzing||state.downloading?'disabled':'')+'>'+(state.analyzing?'<span class="spinner"></span>正在解析':'获取视频信息')+'</button></section>';
out+=errorNote();
if(v){
out+='<section class="video-result"><div class="thumb">'+(v.thumbnail?'<img src="'+esc(v.thumbnail)+'" alt="" onerror="this.style.display=\'none\'">':'<span class="thumb-fallback">'+ico('video')+'</span>')</div><div class="video-copy"><div class="video-title">'+esc(v.title)+'</div><div class="video-extra">'+esc(v.uploader||'')+(v.duration?' · '+duration(v.duration):'')+'</div></div></section>';
out+=formatArea();
}
out+=progress();
return out;
}
function downloads(){
var out=top()+'<header class="page-head"><h1>下载</h1></header>';
if(state.downloading)out+='<div class="minor-label">正在下载</div>'+progress();
if(!state.history||!state.history.length){
out+='<section class="history"><div class="empty"><div class="empty-mark">'+ico('folder')+'</div><strong>暂无下载</strong>完成的文件会显示在这里</div></section>';
}else{
out+='<div class="minor-label">已完成 · '+state.history.length+'</div><section class="history">';
state.history.forEach(function(item,i){
var date=item.savedAt?new Date(Number(item.savedAt)).toLocaleString():'';
out+='<div class="download-item"><div class="file-symbol">'+ico('video')+'</div><div class="file-copy"><div class="file-title">'+esc(item.title||item.fileName)+'</div><div class="file-detail">'+esc(item.fileName)+' · '+esc(date)+'</div></div><button class="open-file" data-open="'+i+'" aria-label="打开">'+ico('open')+'</button></div>';
});
out+='</section>';
}
return out;
}
function settingRow(name,value,iconName,action,label,danger){
return '<div class="setting"><span class="setting-icon">'+ico(iconName)+'</span><div class="setting-copy"><div class="setting-name">'+name+'</div><div class="setting-value">'+value+'</div></div>'+(action?'<button class="setting-action '+(danger?'danger':'')+'" data-action="'+action+'">'+label+'</button>':'')+'</div>';
}
function settings(){
var out=top()+'<header class="page-head"><h1>设置</h1></header>';
out+='<div class="minor-label">存储</div><section class="settings-list">'+settingRow('保存位置','Downloads / VideoDL','folder','','')+settingRow('清理临时文件','删除未完成下载缓存','trash','temp','清理')+'</section>';
out+='<div class="minor-label">应用</div><section class="settings-list">'+settingRow('下载引擎','本机 Python · yt-dlp','settings','','')+settingRow('清空下载记录','不删除手机中的文件','trash','history','清空',true)+'</section>';
if(state.settingsMessage)out+='<div class="message">'+esc(state.settingsMessage)+'</div>';
return out;
}
function bind(){
var input=document.getElementById('video-input'),analyze=document.getElementById('analyze'),paste=document.getElementById('paste');
if(input)input.addEventListener('input',function(){bridge.rememberInput(input.value);});
if(analyze)analyze.addEventListener('click',function(){if(input)bridge.analyze(input.value);});
if(paste)paste.addEventListener('click',function(){bridge.paste();});
document.querySelectorAll('[data-tab]').forEach(function(b){b.addEventListener('click',function(){var t=Number(b.dataset.tab),items=t===0?(state.video.videoFormats||[]):(state.video.audioFormats||[]);state.selectedFormatTab=t;state.selectedFormatKey=items.length?items[0].key:'';bridge.selectFormat(state.selectedFormatKey,t);});});
document.querySelectorAll('[data-select]').forEach(function(b){b.addEventListener('click',function(){bridge.selectFormat(b.dataset.select,state.selectedFormatTab);});});
document.querySelectorAll('[data-expand]').forEach(function(b){b.addEventListener('click',function(){expanded[b.dataset.expand]=!expanded[b.dataset.expand];render();});});
document.querySelectorAll('[data-download]').forEach(function(b){b.addEventListener('click',function(){bridge.download(b.dataset.download);});});
document.querySelectorAll('[data-open]').forEach(function(b){b.addEventListener('click',function(){bridge.openDownload(Number(b.dataset.open));});});
document.querySelectorAll('[data-action]').forEach(function(b){b.addEventListener('click',function(){if(b.dataset.action==='temp')bridge.clearTemp();if(b.dataset.action==='history'&&window.confirm('清空下载记录？手机里的文件不会删除。'))bridge.clearHistory();});});
}
function render(){app.innerHTML=state.page===0?home():state.page===1?downloads():settings();nav();bind();}
window.VideoDLRender=function(next){if(!next)return;state=Object.assign({},state,next);render();};
document.querySelectorAll('.nav-item').forEach(function(b){b.addEventListener('click',function(){bridge.navigate(Number(b.dataset.page));});});
render();
})();