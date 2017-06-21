package com.hello2morrow.sonargraph.integration.sonarqube.foundation;

import java.util.List;

import com.hello2morrow.sonargraph.integration.access.model.IIssue;
import com.hello2morrow.sonargraph.integration.access.model.IIssueProvider;
import com.hello2morrow.sonargraph.integration.access.model.IIssueType;
import com.hello2morrow.sonargraph.integration.access.model.INamedElement;
import com.hello2morrow.sonargraph.integration.access.model.IResolution;

public class StandardFixResolutionIssue implements IIssue
{
    private static final long serialVersionUID = -6480457296024358084L;
    private final IIssue issue;
    private final IResolution resolution;

    public StandardFixResolutionIssue(final IResolution resolution, final IIssue issue)
    {
        this.resolution = resolution;
        this.issue = issue;
    }

    @Override
    public IIssueProvider getIssueProvider()
    {
        return new StandardFixIssueProvider();
    }

    protected IResolution getResolution()
    {
        return resolution;
    }

    @Override
    public IIssueType getIssueType()
    {
        return new FixResolutionIssueType();
    }

    @Override
    public boolean hasResolution()
    {
        return true;
    }

    protected IIssue getIssue()
    {
        return issue;
    }

    @Override
    public String getDescription()
    {
        return resolution.getDescription() + ", assignee=" + getResolution().getAssignee() + ", created=" + getResolution().getDate() + "\n"
                + issue.getDescription();
    }

    @Override
    public List<INamedElement> getOrigins()
    {
        return issue.getOrigins();
    }

    @Override
    public int getLineNumber()
    {
        return issue.getLineNumber();
    }
}
