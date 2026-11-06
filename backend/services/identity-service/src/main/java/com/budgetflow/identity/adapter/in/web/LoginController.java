package com.budgetflow.identity.adapter.in.web;

import com.budgetflow.identity.config.IdentityProperties;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** The branded sign-in page; Spring Security still handles POST /login. */
@Controller
class LoginController {

    private final IdentityProperties properties;

    LoginController(IdentityProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/login")
    String login(@RequestParam(required = false) String error, @RequestParam(required = false) String logout, Model model) {
        model.addAttribute("failed", error != null);
        model.addAttribute("signedOut", logout != null);
        model.addAttribute("signupUrl", properties.webSignupUrl());
        return "login";
    }
}
