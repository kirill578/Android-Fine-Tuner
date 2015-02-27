/**
 * Listens for the app launching then creates the window
 *
 * @see http://developer.chrome.com/apps/app.runtime.html
 * @see http://developer.chrome.com/apps/app.window.html
 */
chrome.app.runtime.onLaunched.addListener(function() {
  // Center window on screen.
  var screenWidth = screen.availWidth;
  var screenHeight = screen.availHeight;
  var width = 1200;
  var height = 800;

  chrome.app.window.create('index.html', {
    id: "AndroidFineTunerID",
    outerBounds: {
      width: width,
      height: height,
      minWidth: 800,
      minHeight: 600, 
      left: Math.round((screenWidth-width)/2),
      top: Math.round((screenHeight-height)/2)
    }
  }, function(win) {
   	win.maximize();
  });
});
