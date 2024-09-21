package org.oopscraft.fintics.view;

import lombok.RequiredArgsConstructor;
import org.oopscraft.fintics.model.Order;
import org.oopscraft.fintics.model.Strategy;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

@Controller
@RequestMapping("strategies")
@PreAuthorize("hasAuthority('strategies')")
@RequiredArgsConstructor
public class StrategiesController {

    @GetMapping
    public ModelAndView getStrategies() {
        ModelAndView modelAndView = new ModelAndView("strategies.html");
        modelAndView.addObject("languages", Strategy.Language.values());
        return modelAndView;
    }

}
