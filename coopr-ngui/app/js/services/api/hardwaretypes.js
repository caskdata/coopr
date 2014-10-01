angular.module(PKG.name+'.services').factory('myApi_hardwaretypes', 
function ($resource, myApiPrefix) {

  return {

    HardwareType: $resource(myApiPrefix + 'hardwaretypes/:name',
      { name: '@name' },
      { 
        update: {
          method: 'PUT'
        },
        save: {
          method: 'POST',
          url: myApiPrefix + 'hardwaretypes',
          params: {name: null}
        }
      }
    )

  };

});

