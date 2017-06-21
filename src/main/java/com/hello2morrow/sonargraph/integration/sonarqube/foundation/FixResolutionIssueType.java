package com.hello2morrow.sonargraph.integration.sonarqube.foundation;

import com.hello2morrow.sonargraph.integration.access.model.IIssueCategory;
import com.hello2morrow.sonargraph.integration.access.model.IIssueProvider;
import com.hello2morrow.sonargraph.integration.access.model.IIssueType;
import com.hello2morrow.sonargraph.integration.access.model.Severity;

public class FixResolutionIssueType implements IIssueType
{
    private static final long serialVersionUID = 8673500336397065613L;
    public static final String FIX_RESOLUTION_RULE = "FixResolution";

    @Override
    public String getName()
    {
        return FIX_RESOLUTION_RULE;
    }

    @Override
    public String getPresentationName()
    {
        return "Fix Resolution";
    }

    @Override
    public IIssueCategory getCategory()
    {
        return new FixResolutionIssueCategory();
    }

    @Override
    public Severity getSeverity()
    {
        return Severity.INFO;
    }

    @Override
    public IIssueProvider getProvider()
    {
        return null;
    }

    @Override
    public String getDescription()
    {
        return "Sonargraph 'Fix' Resolution";
    }
}
