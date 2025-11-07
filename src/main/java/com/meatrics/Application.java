package com.meatrics;

import com.meatrics.util.JooqCodeGenerator;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.theme.Theme;
import com.vaadin.flow.theme.lumo.Lumo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@Theme(value = "default", variant = Lumo.DARK)
public class Application implements AppShellConfigurator {

    public static void main(String[] args) {
        if (false) {
            var context = SpringApplication.run(Application.class, args);
            var generator = context.getBean(JooqCodeGenerator.class);
            try {
                generator.generateJooqClasses();
                System.out.println("Done!");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        SpringApplication.run(
                Application.class, args);
    }

}
