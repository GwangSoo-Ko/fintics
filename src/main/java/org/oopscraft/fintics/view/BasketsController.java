package org.oopscraft.fintics.view;

import lombok.RequiredArgsConstructor;
import org.oopscraft.fintics.model.Strategy;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

@Controller
@RequestMapping("baskets")
@PreAuthorize("hasAuthority('baskets')")
@RequiredArgsConstructor
public class BasketsController {

    @GetMapping
    public ModelAndView getBaskets() {
        ModelAndView modelAndView = new ModelAndView("baskets.html");
        return modelAndView;
    }

}
