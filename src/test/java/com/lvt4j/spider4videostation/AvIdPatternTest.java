package com.lvt4j.spider4videostation;

import static com.lvt4j.spider4videostation.Consts.AvIdPattern;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 *
 * @author LV on 2022年7月3日
 */
public class AvIdPatternTest {

    @Test
    public void test() {
        assertTrue("STAR-507".matches(AvIdPattern));
        assertTrue("STAR-PPV-507".matches(AvIdPattern));
        assertFalse("STAR-PPV-507-".matches(AvIdPattern));
        assertFalse("STAR_507".matches(AvIdPattern));
        assertFalse("STAR-507 ".matches(AvIdPattern));
        
        assertEquals("CYAS-005", Utils.extractAvId("CYAS-005 初美沙希 AAA-bbb"));
        assertEquals("CYAS-005", Utils.extractAvId("CYAS-005-初美沙希-AAA-bbb"));
        assertEquals("CYAS-005", Utils.extractAvId("CYAS-005 cn 初美沙希 AAA-bbb"));
        assertEquals("CYAS-005", Utils.extractAvId("CYAS-005CH 初美沙希 AAA-bbb"));
        assertEquals("CYAS-005", Utils.extractAvId("CYAS-005hd 初美沙希 AAA-bbb"));
        assertEquals("CYAS-005", Utils.extractAvId("CYAS-005 hd 初美沙希 AAA-bbb"));
        assertEquals("CYAS-005", Utils.extractAvId("CYAS-005fhd 初美沙希 AAA-bbb"));
        assertEquals("CYAS-005", Utils.extractAvId("CYAS-005 FHD 初美沙希 AAA-bbb"));
    }
    
}