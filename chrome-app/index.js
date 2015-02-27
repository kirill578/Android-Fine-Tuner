var socketID = 0;
var selected_hashcode = 0;

var hashcode_request = 0;
document.addEventListener('DOMContentLoaded', function() {
    	var ip = document.getElementById('ip');

	chrome.storage.local.get('ip', function (result) {
		if (result.ip !== undefined)
			ip.value = result.ip;
    	});



	 $('#model_btn').tooltip();
	

	$("#login_error_text").hide();
	$("#dashboard").hide();

	$("#dashboard").dblclick(function() {
	   $('#myModal').modal('show') ;
	});

	setTimeout(function (){
		$("#login_box").animate({ opacity: 1, top: "50%"}, 1200, "easeOutCirc", function() { $("#ip").focus(); });
	}, 200);

	location.reload();
	document.getElementById('connect').addEventListener('click', function() {
		$("#ip_div").removeClass("has-error", 100);
		$("#login_error_text").hide("blind", 100);
		chrome.sockets.tcp.create({'bufferSize':4096},
		    function (createInfo) {
			console.log(createInfo.socketId);
			chrome.sockets.tcp.connect(createInfo.socketId,
			    ip.value, 6000,
			    function (result) {
				if (chrome.runtime.lastError) {
				    console.log(chrome.runtime.lastError.message);
				    $("#ip_div").addClass("has-error", 1000);
				    $("#login_error_text").text(chrome.runtime.lastError.message);
				    $("#ip").focus();
			            $("#login_error_text").show( "blind", 500);
				} else {
				    socketID = createInfo.socketId;
				    console.log(result);
					chrome.sockets.tcp.onReceiveError.addListener(function(info) {
				    		 chrome.runtime.reload();
				    });

				  chrome.storage.local.set({'ip': ip.value})

				  $("#login_box").animate({ opacity: 0, top: "-20%"}, 800, "easeInBack");

				  send({ "request": "getTree" });


				  $("#dashboard").show("clip", 1000);

				  chrome.sockets.tcp.setPaused(socketID, false);
				}
			    }
			);
		    }
		);

    	});

	document.getElementById('getTree').addEventListener('click', function() {
		send({ "request": "getTree" });
	});


	document.getElementById('getTree').addEventListener('click', function() {
		send({ "request": "autoFind" });
	});


	document.getElementById('apply').addEventListener('click', function() {
		var inputs = $("#editor").find(":input");
		var hashtoappy = $("#editor_hashcode").val();
		var newValues = [];
		jQuery.each(inputs, function(key, input) {
				var key = input.id.replace("editor_","");
				var singleItem = {};
				singleItem[key] = input.value;
				newValues.push(singleItem);
		});
		send({"request": "newValues", "values":newValues, "hashcode": hashtoappy});
    	});

});

var buffer = "";
chrome.sockets.tcp.onReceive.addListener(
    function (info) {
	if (info.data) {
		var str = ab2str(info.data)
		buffer += str;
		try {
			var response = JSON.parse(buffer);
			onData(response);
			buffer = "";
		} catch (error) {
		}
	}
	    
    }
);

function send(json) {
	buffer = "";
	chrome.sockets.tcp.send(socketID,
		str2ab(JSON.stringify(json) + "\n"),
		function (sendInfo) {
			console.log(sendInfo);
		}
	);
}

function onData(object) {
	if( object.response == "tree" ) {
		var data = object.data;
		$('#data').html('<div id="data_true"></div>');
		$('#data_true').jstree({ 'core' : { 'data' : [ {'text':'empty'}] } });
		$('#data_true').on('changed.jstree', function (e, data) {
			var node = data.instance.get_node(data.selected[0]);
			var org = node.original;
			selected_hashcode = org.hashcode;
			send({"request": "getValues", "hashcode":selected_hashcode});
			send({"request": "getImage", "hashcode":selected_hashcode});
		});
		$('#data_true').jstree(true).settings.core.data = [data];
		$('#data_true').jstree(true).settings.core.dblclick_toggle = false;
		$('#data_true').jstree(true).refresh();

	}
	else if( object.response == "image" ) {
		var data = object.data;
		if(data != "error")
			document.getElementById('image').src = data;
		else
			document.getElementById('image').src = "error.jpg";
	}
	else if( object.response == "values" ) {
		var data = object.data;
		container = document.getElementById('editor');
		while (container.hasChildNodes()) {
    		container.removeChild(container.lastChild);
		}
		jQuery.each(data, function(key, value) {
			$('#editor').append('<div class="form-group form-inline" id="group_editor_'+key+'" ><label style="width: 200px;" for="editor_'+key+'">'+key+'</label>  <div class="input-group" id="editor_group_'+key+'"><input type="text" class="form-control" style="width: 100px;" id="editor_'+key+'" value="'+value+'"></div> </div>');
		});
		$('#group_editor_hashcode').hide();

		$('#editor_group_height').append('<span class="input-group-addon">px</span>');
		$('#editor_group_width').append('<span class="input-group-addon">px</span>');
		$('#editor_group_paddingTop').append('<span class="input-group-addon">px</span>');
		$('#editor_group_paddingBottom').append('<span class="input-group-addon">px</span>');
		$('#editor_group_paddingLeft').append('<span class="input-group-addon">px</span>');
		$('#editor_group_paddingRight').append('<span class="input-group-addon">px</span>');
		$('#editor_group_marginTop').append('<span class="input-group-addon">px</span>');
		$('#editor_group_marginBottom').append('<span class="input-group-addon">px</span>');
		$('#editor_group_marginLeft').append('<span class="input-group-addon">px</span>');
		$('#editor_group_marginRight').append('<span class="input-group-addon">px</span>');
		$('#editor_group_textSize').append('<span class="input-group-addon">px</span>');
		
		$('#editor_group_textColor').prepend('<span class="input-group-addon">#</span>');
		$('#editor_group_backgroundColor').prepend('<span class="input-group-addon">#</span>');

		$('#editor_group_textColor').append('<span class="input-group-addon"><div>Click2Pick</div></span>');
		$('#editor_group_backgroundColor').append('<span class="input-group-addon"><div>Click2Pick</div></span>');

		var bgPicker = $('#editor_group_backgroundColor').find('div');
		bgPicker.colorpicker({format:"rgba"}).on('changeColor', function(ev) {
			//bgPicker.css("background-color", ev.color.toHex());
			$('#editor_backgroundColor').val(pad(argb(ev.color.toRGB()),8));
		});

		var textPicker = $('#editor_group_textColor').find('div');
		textPicker.colorpicker({format:"rgba"}).on('changeColor', function(ev) {
			//bgPicker.css("background-color", ev.color.toHex());
			$('#editor_textColor').val(pad(argb(ev.color.toRGB()),8));
		});

	}
	else if( object.response == "applied" ) {
		send({"request": "getValues", "hashcode":selected_hashcode});
		send({"request": "getImage", "hashcode":selected_hashcode});
	}
}

function argb(rgb) {
	var colorValue = new Number( ((parseFloat(rgb.a)*255) << 24) | (parseInt(rgb.r) << 16) | (parseInt(rgb.g) << 8) | parseInt(rgb.b));
	return getHexRepresentation(colorValue, 8);
}

var hex = ['0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'];
function getHexRepresentation(num, symbols) {
    var result = '';
    while (symbols--) {
        result = hex[num & 0xF] + result;
        num >>= 4;
    }
    return result;
}

function hexToRgba(hex) {
    var result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex);
    var tmp = parseInt(result[1], 16);
    var alpha = tmp / 255;
    return result ? {
	a: alpha,
        r: parseInt(result[2], 16),
        g: parseInt(result[3], 16),
        b: parseInt(result[4], 16)
    } : null;
}

function str2ab(str) {
    var encoder = new TextEncoder('utf-8');
    return encoder.encode(str).buffer;
}

function pad(n, width, z) {
  z = z || '0';
  n = n + '';
  return n.length >= width ? n : new Array(width - n.length + 1).join(z) + n;
}

function ab2str(ab) {
    var dataView = new DataView(ab);
    var decoder = new TextDecoder('utf-8');
    return decoder.decode(dataView);
}






