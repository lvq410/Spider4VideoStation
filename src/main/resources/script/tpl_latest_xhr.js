//结合插件XHRInterceptorExtension.crx使用，获取最后一次访问指定url的请求的响应
var urlPrefix = '@@UrlPrefix@@';

var xhr;
for (var i = XHRIntercepted.length - 1; i >= 0; i--) {
    if(!XHRIntercepted[i].responseURL.startsWith(urlPrefix)) continue;
    xhr = XHRIntercepted[i];
    break;
}

console.info('最近一次访问', urlPrefix, '的请求：', xhr);

if(!xhr) return;

var response = {
  status: xhr.status,
  headers: xhr.getAllResponseHeaders(),
  responseText: xhr.responseText
}
return JSON.stringify(response)