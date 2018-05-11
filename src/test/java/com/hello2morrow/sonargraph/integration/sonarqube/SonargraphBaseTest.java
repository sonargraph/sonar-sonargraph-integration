package com.hello2morrow.sonargraph.integration.sonarqube;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class SonargraphBaseTest
{
    @Test
    public void testCreateKeysAndNames()
    {
        //Rules
        assertEquals("SCRIPT_ISSUE", SonargraphBase.createRuleKey(SonargraphBase.SCRIPT_ISSUE_NAME));
        assertEquals("Sonargraph Integration: Script Issue", SonargraphBase.createRuleName(SonargraphBase.SCRIPT_ISSUE_PRESENTATION_NAME));
        assertEquals("script-based", SonargraphBase.createRuleCategoryTag(SonargraphBase.SCRIPT_ISSUE_CATEGORY_PRESENTATION_NAME));
        //Metrics
        assertEquals("sg_i.Sonargraph.NUMBER_OF_STATEMENTS",
                SonargraphBase.createCustomMetricKeyFromStandardName("Sonargraph", "NumberOfStatements"));
        assertEquals("sg_i.NUMBER_OF_STATEMENTS", SonargraphBase.createMetricKeyFromStandardName("NumberOfStatements"));
    }
}