/**
 * LoginCtrl
 */

angular.module(PKG.name+'.controllers').controller('LoginCtrl', 
function ($scope, myAuth, $alert, $state, cfpLoadingBar, $timeout, MYAUTH_EVENT, myFocusManager) {

  $scope.credentials = myAuth.remembered();

  $scope.submitting = false;

  $scope.doLogin = function (c) {
    $scope.submitting = true;
    cfpLoadingBar.start();

    myAuth.login(c)
      .finally(function(){
        $scope.submitting = false;
        cfpLoadingBar.complete();
      });
  };

  $scope.$on('$viewContentLoaded', function() { 
    if(myAuth.isAuthenticated()) {
      $state.go('home');
      $alert({content:'You are already logged in!', type:'warning', duration:5});
    }
    else {
      focusLoginField();
    }
  });

  $scope.$on(MYAUTH_EVENT.loginFailed, focusLoginField);

  /* ----------------------------------------------------------------------- */

  function focusLoginField() {
    $timeout(function() {
      myFocusManager.select($scope.credentials.username ? 'password' : 'username');
    }, 10); // the addtl timeout is so this triggers AFTER any potential focus() on an $alert
  }

});