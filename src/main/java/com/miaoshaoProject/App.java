package com.miaoshaoProject;

import com.miaoshaoProject.dao.UserDOMapper;
import com.miaoshaoProject.dataobject.UserDO;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication(scanBasePackages = {"com.miaoshaoProject"})
@RestController
@MapperScan("com.miaoshaoProject.dao")
public class App {
    public static void main( String[] args ) {
        SpringApplication.run(App.class,args);
    }
}
