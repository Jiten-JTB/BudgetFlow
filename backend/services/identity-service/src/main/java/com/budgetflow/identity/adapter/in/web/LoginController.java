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

    /** The sign-in server has no home page of its own: its root sends visitors to Budget Flow's. */
    @GetMapping("/")
    String home() {
        java.net.URI signup = java.net.URI.create(properties.webSignupUrl());
        return "redirect:" + signup.resolve("/");
    }

    @GetMapping("/login")
    String login(@RequestParam(required = false) String error, @RequestParam(required = false) String logout,
                 @RequestParam(required = false) String unverified, Model model) {
        model.addAttribute("failed", error != null);
        model.addAttribute("unverified", unverified != null);
        model.addAttribute("signedOut", logout != null);
        model.addAttribute("signupUrl", properties.webSignupUrl());
        return "login";
    }
}
