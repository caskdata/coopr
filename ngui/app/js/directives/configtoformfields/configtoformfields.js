/**
 * Configtoformfields directive.
 *
 * This takes a config and converts fields into form fields bound to a provided object.
 * !! This directive modifies model passed to it from the parent.
 * @param {Object|String} fieldsconfig configuration for fields.
 * Expected config:
 * fields: {
 *   <fieldname>: {
 *     label: string,
 *     override: boolean,
 *     tip: string,
 *     type: string
 *   }
 * },
 * required: []
 *   [<fieldname>, <fieldname>]
 * }
 * @param {Object} model Model to attach field values to.
 * @param {Booelan} overrideenabled Determines whether only override-able form fields should be
 * enabled.                                
 *
 * <div my-configtoformfields fieldsconfig="json" model="model.fields" overrideenabled="false"/>
 */

angular.module(PKG.name+'.directives').directive('myConfigtoformfields', 
function myConfigtoformfieldsDirective ($log) {
  return {
    restrict: 'AE',
    replace: true,
    scope: {
      fieldsconfig: '=',
      model: '=',
      overrideenabled: '='
    },
    templateUrl: 'configtoformfields/configtoformfields.html',
    link: function (scope, element, attrs) {

      scope.$watch('fieldsconfig', function (newVal, oldVal) {

        if (!angular.equals(newVal, oldVal)) {
          if (scope.fieldsconfig) {

            if (typeof scope.fieldsconfig !== 'object') {
              scope.fieldsconfig = angular.fromJson(scope.fieldsconfig);  
            }

            if (!scope.fieldsconfig.hasOwnProperty('fields')) {
              $log.error('fields not declared in json for fieldsconfig');
            }

          }

          setDefaults();
        }

      });

      scope.$watch('model', function (newVal, oldVal) {
        setRequired(newVal);
      }, true);

      function setDefaults () {        
        if (!scope.fieldsconfig || !scope.model) {
          return;
        }
        angular.forEach(scope.fieldsconfig.fields, function (field, key) {
          if (field.hasOwnProperty('default') && !scope.model[key]) {
            scope.model[key] = field.default;
          }
        });
      }

      function setRequired (newVal) {
        if (!scope.fieldsconfig || !scope.model) {
          return;
        }
        
        var out = {};
        angular.forEach(newVal, function (val, key) {
          if(val && !out[key]) {
            angular.forEach(scope.fieldsconfig.required, function (set) {
              if(set.indexOf(key)>=0) {
                out = transformRequired(set);
              }
            });
          }
        });

        scope.required = Object.keys(out).length ? out 
          : transformRequired(scope.fieldsconfig.required[0]);
      }

      /**
       * Transforms required fields array into an object for template.
       * @param  {Array} requiredFields.
       * @return {Object} required {<fieldname>:true}
       */
      function transformRequired(requiredFields) {
        var required = {};
        angular.forEach(requiredFields, function (item) {
          required[item] = true;
        });
        return required;
      }

    }
  };

});
