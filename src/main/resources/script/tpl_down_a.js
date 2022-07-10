var body = document.getElementsByTagName('body')[0];
var a = document.createElement('a');
a.setAttribute('href', '@@OrigUrl@@');
a.setAttribute('download', '');
body.appendChild(a);
a.click();