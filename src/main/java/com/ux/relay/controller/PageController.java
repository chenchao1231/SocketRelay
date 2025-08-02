package com.ux.relay.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 页面控制器
 * 
 * @author 小白很菜
 * @version 1.0
 * @since 2025-08-02
 */
@Controller
public class PageController {
    
    /**
     * 主页
     */
    @GetMapping("/")
    public String index() {
        return "forward:/index.html";
    }
    
    /**
     * 登录页面
     */
    @GetMapping("/login")
    public String login() {
        return "forward:/login.html";
    }
}
