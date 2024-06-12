var xhr = new XMLHttpRequest();
xhr.open("GET", location.href, false);
xhr.setRequestHeader("Accept-Language", "zh-CN");
xhr.send();
return xhr.responseText;