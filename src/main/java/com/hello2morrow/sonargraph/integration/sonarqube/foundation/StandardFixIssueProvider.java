package com.hello2morrow.sonargraph.integration.sonarqube.foundation;

import com.hello2morrow.sonargraph.integration.access.model.IIssueProvider;

class StandardFixIssueProvider implements IIssueProvider
{
    private static final long serialVersionUID = -1554771689536851781L;

    @Override
    public String getName()
    {
        return "Sonargraph";
    }

    @Override
    public String getPresentationName()
    {
        return getName();
    }
}
