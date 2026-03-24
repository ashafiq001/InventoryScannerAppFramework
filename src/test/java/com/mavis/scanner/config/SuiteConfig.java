package com.mavis.scanner.config;

import com.mavis.scanner.utils.EmailHelper;
import org.testng.ITestContext;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.BeforeTest;

public class SuiteConfig {

    @BeforeTest(alwaysRun = true)
    public void setupSuite(ITestContext context){
        String suiteName = context.getSuite().getName();
        if(suiteName !=null && !suiteName.equalsIgnoreCase("Default Suite")){
            EmailHelper.enable();
            System.out.println("[SuiteConfig] Email enabled for suite: " + suiteName);
        }else {
            System.out.println("[SuiteConfig] Email disabled for suite: " + suiteName);
        }
    }


}
