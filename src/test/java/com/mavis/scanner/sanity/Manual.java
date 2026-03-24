package com.mavis.scanner.sanity;

import com.mavis.scanner.base.BaseTest;
import com.mavis.scanner.utils.ScanHelper;
import org.testng.annotations.Test;

public class Manual extends BaseTest {
    @Test
    public void myTest() throws InterruptedException {
        attach("My test");
        ScanHelper scan = new ScanHelper(driver,wait);


//        scan.scan("STR-1002");
//        scan.scan("092971282523",2);
//        scan.scan("092971283155",2);
//
//
//        scan.closeSection();


        scan.scan("STR-5008");
        //scan.scan("8936096303782",1);
        //scan.scan("71545961251",1);
        //scan.scan("092971283155",2);
        //scan.scan("00662498001213");

        //scan.scan("26937357203267");
        scan.scan("092971282356");
        scan.scan("09297128235");

       //scan.closeSection();

//        scan.scan("00074130066274");
//        scan.scan("074130048812");
//        scan.scan("074130066304");





    }
}
