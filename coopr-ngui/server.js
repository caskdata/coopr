/**
 * Spins up two web servers
 *   - http-server sends the static assets in dist and handles the /config.json endpoint
 *   - cors-proxy adds the necessary headers for xdomain access to the REST API
 */

var pkg = require('./package.json'),
    morgan = require('morgan'),

    COOPR_UI_PORT = parseInt(process.env.COOPR_UI_PORT || 8080, 10),
    COOPR_CORS_PORT = parseInt(process.env.COOPR_CORS_PORT || 8081, 10),
    COOPR_SERVER_URI = process.env.COOPR_SERVER_URI || 'http://127.0.0.1:55054',

    color = {
      hilite: function (v) { return '\x1B[7m' + v + '\x1B[27m'; },
      green: function (v) { return '\x1B[40m\x1B[32m' + v + '\x1B[39m\x1B[49m'; },
      pink: function (v) { return '\x1B[40m\x1B[35m' + v + '\x1B[39m\x1B[49m'; }
    };


morgan.token('cooprcred', function(req, res){ 
  return color.pink(req.headers['coopr-userid'] + '/' + req.headers['coopr-tenantid']); 
});

var httpLabel = color.green('http-server'),
    corsLabel = color.pink('cors-proxy'),
    httpLogger = morgan(httpLabel+' :method :url', {immediate: true}),
    corsLogger = morgan(corsLabel+' :method :url :cooprcred :status', {
      skip: function(req, res) { return req.method === 'OPTIONS' }
    });

console.log(color.hilite(pkg.name) + ' v' + pkg.version + ' starting up...');

/**
 * HTTP server
 */
require('http-server')
  .createServer({
    root: __dirname + '/dist',
    before: [
      httpLogger,
      function (req, res) {
        var reqUrl = req.url.match(/^\/config\.(js.*)/);

        if(!reqUrl) {
          // all other paths are passed to ecstatic
          return res.emit('next');
        }

        var data = JSON.stringify({
          // the following will be available in angular via the "MY_CONFIG" injectable

          COOPR_SERVER_URI: COOPR_SERVER_URI,
          COOPR_CORS_PORT: COOPR_CORS_PORT,
          authorization: req.headers.authorization

        });

        var contentType;

        if(reqUrl[1] === 'json') {
          contentType = 'application/json';
        }
        else { // want JS
          contentType = 'text/javascript';
          data = 'angular.module("'+pkg.name+'.config", [])' + 
                    '.constant("MY_CONFIG",'+data+');';
        }

        res.writeHead(200, { 
          'Content-Type': contentType,
          'Cache-Control': 'no-store, must-revalidate'
        });

        res.end(data);
      }
    ]
  })
  .listen(COOPR_UI_PORT, '0.0.0.0', function () {
    console.log(httpLabel+' listening on port %s', COOPR_UI_PORT);
  });


/**
 * CORS proxy
 */
require('cors-anywhere')
  .createServer({
    requireHeader: ['x-requested-with'],
    removeHeaders: ['cookie', 'cookie2']
  })
  .on('request', function (req, res) {
    corsLogger(req, res, function noop() {} );
  })
  .listen(COOPR_CORS_PORT, '0.0.0.0', function() {
    console.log(corsLabel+' listening on port %s', COOPR_CORS_PORT);
  });
