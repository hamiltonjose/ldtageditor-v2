(function(){
  // Simple shim: expose AndroidApp object that uses window.__nativeCallback for responses
  window.__nativeCallback = window.__nativeCallback || function(responseJson){
    try {
      var parsed = JSON.parse(responseJson);
      if(parsed && parsed.id){
        // dispatch to any pending promise handlers
        if(window.__android_promises && window.__android_promises[parsed.id]){
          if(parsed.result) window.__android_promises[parsed.id].resolve(parsed.result);
          else window.__android_promises[parsed.id].reject(parsed.error);
          delete window.__android_promises[parsed.id];
        }
      }
    } catch(e){ console.error('Native callback parse error', e); }
  };

  window.AndroidApp = {
    __android_promises: {},
    _callNative: function(method, params){
      var id = Math.random().toString(36).substr(2,9);
      var payload = { id: id, method: method, params: params };
      var json = JSON.stringify(payload);
      if(!window.__android_promises) window.__android_promises = {};
      var p = new Promise(function(resolve, reject){
        window.__android_promises[id] = { resolve: resolve, reject: reject };
      });
      try {
        // Try modern postMessage
        if(window.AndroidAppBridge && window.AndroidAppBridge.postMessage){
          window.AndroidAppBridge.postMessage(json);
        } else if(window.AndroidAppBridgeFallback && window.AndroidAppBridgeFallback.handleMessage){
          window.AndroidAppBridgeFallback.handleMessage(json);
        } else {
          console.warn('No Android bridge available');
          window.__android_promises[id].reject('NO_BRIDGE');
        }
      } catch(e){
        window.__android_promises[id].reject('BRIDGE_ERROR');
      }
      return p;
    },
    checkNfcAvailable: function(){ return this._callNative('checkNfcAvailable', {}); },
    startScan: function(mode){ return this._callNative('startScan', { mode: mode }); },
    stopScan: function(){ return this._callNative('stopScan', {}); },
    readPage: function(page){ return this._callNative('readPage', { page: page }); },
    writePage: function(page, base64){ return this._callNative('writePage', { page: page, data: base64 }); }
  };
})();
