package com.hello2morrow.sonargraph.integration.sonarqube.foundation;

import com.hello2morrow.sonargraph.integration.access.model.IIssueCategory;

public class FixResolutionIssueCategory implements IIssueCategory
{
    private static final long serialVersionUID = -3920499764178581468L;

    @Override
    public String getName()
    {
        return "Fix";
    }

    @Override
    public String getPresentationName()
    {
        return getName();
    }
}
