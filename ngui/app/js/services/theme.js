/**
 * A service that provides support for themes.
 * @type {[type]}
 */
var module = angular.module(PKG.name+'.services');

module.service('myTheme', 
  function myThemeService ($localStorage, $rootScope, MYTHEME_NAMES, MYTHEME_EVENT) {

  this.current = $localStorage.theme || MYTHEME_NAMES[0];
  
  this.set = function (theme) {
    if (MYTHEME_NAMES.indexOf(theme)!==-1) {
      this.current = theme;
      $localStorage.theme = theme;
      $rootScope.$broadcast(MYTHEME_EVENT.changed, this.getClassName());
    }
  };

  this.list = function () {
    return MYTHEME_NAMES;
  };

  this.getClassName = function () {
    return 'theme-' + this.current;
  };

});
