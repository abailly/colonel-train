var loader = {
  show:function () {
    $('#loader').show();
  },

  hide:function () {
    $('#loader').hide();
  }
};

handleErrorReponse = function (response) {
  var error = "Server error [500]: ";

  var asHtml = $('<html />').html(response);

  // Is a Server error or an exception mapped by ApplicationExceptionMapper
  if ($(asHtml).find("pre").length <= 0) {
    return error + response.replace(/\n/g, "<br />").replace(/\r/g, "");
  }

  var message = $(asHtml).find("pre").first().text();
  var resource = $(asHtml).find("p").first().text();
  var stackTrace = $(asHtml).find("pre:eq(1)").text();

  console.log("Error:")
  console.log("  resource: " + resource);
  console.log("  message: " + message);
  console.log("  stackTrace: " + stackTrace);

  stackTrace = stackTrace.replace(/\n/g, "<br />").replace(/\r/g, "");

  error += resource + " <b>" + message + "</b><br/>" + stackTrace;

  return error;
};

$(function () {
  var showError = function (msg) {
    $('#error-panel').show().html(msg);
  }

  $('#error-panel').click(function () {
    $(this).fadeOut();
  });

  $.ajaxSetup({
    error:function (jqXHR, exception) {
      loader.hide();
      if (jqXHR.status === 0) {
        showError('Not connected.\n Verify Network.');
      } else if (jqXHR.status == 404) {
        showError('Requested page not found. [404]');
      } else if (jqXHR.status == 500) {
        var error = jqXHR.responseText;
        if (error == "")
          error = jqXHR.statusText;
        showError(handleErrorReponse(error));
      } else if (exception === 'parsererror') {
        showError('Requested JSON parse failed.');
      } else if (exception === 'timeout') {
        showError('Time out error.');
      } else if (exception === 'abort') {
        showError('Ajax request aborted.');
      } else {
        showError('Uncaught Error : ' + jqXHR.responseText);
      }
    }
  });

  function select_all_checkboxes() {
    $(':checkbox[@name = "checkbox-ticket"]').attr('checked',true);
  };

  function unselect_all_checkboxes() {
    $(':checkbox[@name = "checkbox-ticket"]').attr('checked',false);
  };

  $('#tickets-view').on('change', '#select-all', function(){
        if($(this).attr("checked")) {
          select_all_checkboxes();
        } else {
          unselect_all_checkboxes();
        }
    });

  $('#selection').submit(function (e) {
    loader.show();
    $.ajax({
      type: "POST",
      url: "/tickets/list",
      dataType: "html",
      data: $(this).serialize(),
      success: function (data) {
        loader.hide();
        $('#tickets-view').html(data);
        window.location.hash = "#tickets";
      }
    });
    e.preventDefault();
    return false;
  });
});
