/**
 * helper functions for e2e tests
 */

module.exports = {

  isLoggedIn: isLoggedIn,

  logout: logout,

  login: login,

  loginAsAdmin: function() {
    login('superadmin', 'admin', 'admin');
  }

};


// ----------------------------------------------------------------------------

function login(tenant, username, password) {
  browser.get('/login');
  browser.waitForAngular();

  isLoggedIn()
    .then(function (needLogout) {
      if(needLogout) {
        logout();
      }
      element(by.id('loginTenant')).clear().sendKeys(tenant);
      element(by.id('loginUsername')).clear().sendKeys(username);
      element(by.id('loginPassword')).clear().sendKeys(password);
      element(by.partialButtonText('Submit')).click();

      browser.wait(function () {
        return browser.getCurrentUrl().then(function (url) {
          return url === 'http://localhost:8080/';
        });
      });
    });
}


function logout() {

  browser.get('/');

  isLoggedIn()
    .then(function (needLogout) {
      if(needLogout) {

        ddIsOpen()
          .then(function (dd) {
            if(!dd) {
              element(by.css('header .navbar-right .dropdown-toggle')).click();
              browser.wait(ddIsOpen, 10000);        
            }

            element(by.css('.dropdown-menu a[ng-click^="logout"]')).click();

            browser.wait(function () {
              return element(
                by.cssContainingText('#alerts .alert-info', 'You are now logged out')
              ).isPresent();
            }, 10000);
          });
      }
    });

}

function isLoggedIn () {
  return element(
    by.css('header .dropdown-toggle .fa-user')
  ).isPresent();
};

function ddIsOpen () {
  return element(
    by.css('header .dropdown.open .dropdown-menu')
  ).isPresent();
}

