console.info('XHR Interceptor init start')

var newScript = document.createElement('script');
newScript.type="text/javascript"
newScript.innerHTML = `console.info("Intercepted XHR will be in window.XHRIntercepted");
window.XHRIntercepted = [];
var oldXHROpen = window.XMLHttpRequest.prototype.open;
window.XMLHttpRequest.prototype.open = function() {
  window.XHRIntercepted.push(this);
  return oldXHROpen.apply(this, arguments);
};
`;

function checkAndInject(){
    if(!document.head){
        //console.info('document.head is null');
        setTimeout(checkAndInject, 1);
        return;
    }
    document.head.appendChild(newScript);
    console.info('XHR Interceptor init end')
}

checkAndInject();