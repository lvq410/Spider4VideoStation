var Url = '@@OrigUrl@@';
var Rst64;

function toBase64(buffer) {
    for(var r,n=new Uint8Array(buffer),t=n.length,a=new Uint8Array(4*Math.ceil(t/3)),i=new Uint8Array(64),o=0,c=0;64>c;++c)
        i[c]="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".charCodeAt(c);for(c=0;t-t%3>c;c+=3,o+=4)r=n[c]<<16|n[c+1]<<8|n[c+2],a[o]=i[r>>18],a[o+1]=i[r>>12&63],a[o+2]=i[r>>6&63],a[o+3]=i[63&r];
        return t%3===1?(r=n[t-1],a[o]=i[r>>2],a[o+1]=i[r<<4&63],a[o+2]=61,a[o+3]=61):t%3===2&&(r=(n[t-2]<<8)+n[t-1],a[o]=i[r>>10],a[o+1]=i[r>>4&63],a[o+2]=i[r<<2&63],a[o+3]=61),new TextDecoder("ascii").decode(a)
}
function stringToArrayBuffer(str) {
    var buf = new ArrayBuffer(str.length);
    var bufView = new Uint8Array(buf);

    for (var i=0, strLen=str.length; i<strLen; i++) {
        bufView[i] = str.charCodeAt(i);
    }
    return buf;
}

console.log('begin load url', Url);

var xhr = new XMLHttpRequest();
xhr.overrideMimeType('text/plain; charset=x-user-defined');
xhr.open('GET', Url, false);
xhr.send();

if(xhr.status != 200) {
    console.log('load err', xhr);
    return;
}

console.log('load url suc', xhr);

arrBuf = stringToArrayBuffer(xhr.response);
console.log('arrBuf', arrBuf);

Rst64 = toBase64(arrBuf);
console.log('loaded 64', Rst64);

return Rst64;