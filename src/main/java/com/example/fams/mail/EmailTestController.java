package com.example.fams.mail;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class EmailTestController {
    @Autowired
    private EmailService emailService;

    @PostMapping("/send-test-email")
    public String sendTestEmail() {
        emailService.sendEmail("akinrindeakintomy@gmail.com", "Test Email", "This is a test email.");
        return "redirect:/dashboard";
    }
}
