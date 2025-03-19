package piper1970.notificationservice.controller;


import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/api/notifications")
public class ConfirmationController {

  // get method should capture the confirmationString in the path,
  // and try to update the confirmation, sending appropriate kafka messages to all

}
