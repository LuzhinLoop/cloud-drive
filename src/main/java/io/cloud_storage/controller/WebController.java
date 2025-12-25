package io.cloud_storage.controller;


import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebController {

    @GetMapping({"/files", "/files/**", "/login", "/registration"})
    public String forward() {
        return "forward:/index.html";
    }
}
