/**
 * Cluster detail controller. [TODO]
 */

var module = angular.module(PKG.name+'.controllers');

module.controller('ClusterListCtrl', function ($scope, $filter, $timeout, myApi, CrudListBase) {
  // Why do this?
  CrudListBase.apply($scope);

  var timeoutPromise,
      filterFilter = $filter('filter'),
      tenMinutesAgo = moment().minutes(-10),
      activePredicate = function (item) { 
        // any cluster created recently is considered "active" for display purposes
        return (moment(item.createTime)>tenMinutesAgo) || (item.status!=='terminated');
      };


  $scope.$watchCollection('list', function (list) {
    if (list.length) {

      var activeCount = filterFilter(list, activePredicate).length,
          filteredCount = list.length - activeCount;
      // show the button and filter only if there are both visible and filterable items
      $scope.listFilterExp = (activeCount && filteredCount) ? activePredicate : null;

      updatePending();
    }
  });

  $scope.$on('$destroy', function () {
    $timeout.cancel(timeoutPromise);
  });

  function updatePending () {
    if(filterFilter($scope.list, {status:'pending'}).length) {
      timeoutPromise = $timeout(function () {

        myApi.Cluster.query(function (list) {
          // $scope.list = list works, but then we lose the animation of progress bars
          // instead we only modify the properties that interest us
          angular.forEach($scope.list, function (cluster) {
            if(cluster.status === 'pending') {
              var update = filterFilter(list, {id:cluster.id});
              if(update && update.length) {
                cluster.status = update[0].status;
                cluster.progress = update[0].progress;
              }
            }
          });

          updatePending();
        });

      },
      1000);
    }
  }
});



