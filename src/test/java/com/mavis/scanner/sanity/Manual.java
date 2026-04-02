package com.mavis.scanner.sanity;

import com.mavis.scanner.base.BaseTest;
import com.mavis.scanner.utils.ScanHelper;
import org.testng.annotations.Test;

public class Manual extends BaseTest {
    @Test
    public void myTest() throws InterruptedException {
        attach("My test");
        ScanHelper scan = new ScanHelper(driver,wait);


        //scan.scan("STR-5000");
        //scan.scan("092971282523",2);
       //scan.scan("092971283155",2);

//       scan.closeSection();

       // scan.scanBattery("017724660915");
        //scan.scan("STR-1003");
       //scan.scan("017724660915");
        //scan.scan("017724684218");
        //scan.scan("017724660717");
        //scan.scan("00662498001213");

       // scan.scan("26937357203267",2);
        //scan.scan("092971282356");
        //scan.scan("09297128235");

       //scan.closeSection();

        scan.scanBatteryReturn("017724660915",2);







    }
}
