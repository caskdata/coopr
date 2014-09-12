/**
 * API service module.
 */

var module = angular.module(PKG.name+'.services');

module.factory('MYAPI_PREFIX', function($location){
  return $location.protocol() + '://' + $location.host() + 
                ':8081/0.0.0.0:55054/v2/';
});

module.factory('myApi', function(
    myApi_clusters,
    myApi_hardwaretypes,
    myApi_imagetypes,
    myApi_providers,
    myApi_provisioners,
    myApi_services,
    myApi_templates,
    myApi_tenants,
    myApi_importexport
  ){

  return angular.extend({}, 
    myApi_clusters,
    myApi_hardwaretypes,
    myApi_imagetypes,
    myApi_providers,
    myApi_provisioners,
    myApi_services,
    myApi_templates,
    myApi_tenants,
    myApi_importexport
  );

});

/**
 * TODO Add comment with what this is supposed to do.
 * @param  {[type]} $httpProvider [description]
 * @return {[type]}               [description]
 */
module.config(function ($httpProvider) {
  $httpProvider.interceptors.push(
    function ($q, $timeout, $rootScope, $log, myAuth, MYAPI_PREFIX, MYAPI_EVENT) {
    var isApi = function(url) {
      return url.indexOf(MYAPI_PREFIX) === 0;
    };

    return {
     'request': function(config) {
        if(isApi(config.url)) {
          var u = myAuth.currentUser;
          angular.extend(config.headers, {
            'X-Requested-With': angular.version.codeName
          }, u ? {
            'X-Loom-UserID': u.username,
            'X-Loom-TenantID': u.tenant
          } : {});
          $log.log('[myApi]', config.method, config.url.substr(MYAPI_PREFIX.length));
        }
        return config;
      },

     'responseError': function(rejection) {
        if(isApi(rejection.config.url)) {
          $rootScope.$broadcast(MYAPI_EVENT.error, rejection);
        }
        return $q.reject(rejection);
      }

    };
  });
});
