package com.hello2morrow.sonargraph.integration.sonarqube.foundation;

import java.util.List;

import com.hello2morrow.sonargraph.integration.access.model.IDuplicateCodeBlockIssue;
import com.hello2morrow.sonargraph.integration.access.model.IDuplicateCodeBlockOccurrence;
import com.hello2morrow.sonargraph.integration.access.model.IIssueProvider;
import com.hello2morrow.sonargraph.integration.access.model.IIssueType;
import com.hello2morrow.sonargraph.integration.access.model.INamedElement;
import com.hello2morrow.sonargraph.integration.access.model.IResolution;

public class DuplicateFixResolutionIssue extends StandardFixResolutionIssue implements IDuplicateCodeBlockIssue
{
    private static final long serialVersionUID = -8852427965647146746L;

    public DuplicateFixResolutionIssue(final IResolution resolution, final IDuplicateCodeBlockIssue issue)
    {
        super(resolution, issue);
    }

    @Override
    public IIssueProvider getIssueProvider()
    {
        return new StandardFixIssueProvider();
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

    @Override
    public String getName()
    {
        return ((IDuplicateCodeBlockIssue) getIssue()).getName();
    }

    @Override
    public List<INamedElement> getAffectedElements()
    {
        return ((IDuplicateCodeBlockIssue) getIssue()).getAffectedElements();
    }

    @Override
    public String getPresentationName()
    {
        return ((IDuplicateCodeBlockIssue) getIssue()).getPresentationName();
    }

    @Override
    public int getBlockSize()
    {
        return ((IDuplicateCodeBlockIssue) getIssue()).getBlockSize();
    }

    @Override
    public List<IDuplicateCodeBlockOccurrence> getOccurrences()
    {
        return ((IDuplicateCodeBlockIssue) getIssue()).getOccurrences();
    }
}
