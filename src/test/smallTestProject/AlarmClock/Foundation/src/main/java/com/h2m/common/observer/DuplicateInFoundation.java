package com.h2m.common.observer;

import com.h2m.alarm.application.TestForNamespaceCycle;

public class DuplicateInFoundation
{
    private int m_count;

    public DuplicateInFoundation()
    {
        m_count = 0;
        m_count++;
        m_count++;
        m_count++;
        m_count++;
        m_count++;
        m_count++;
        m_count++;
        m_count++;
        m_count++;
        m_count++;
        m_count++;
        m_count++;
        m_count++;
        m_count++;
        m_count++;
        m_count++;
        m_count++;
        m_count++;
        m_count++;
        m_count++;
        m_count++;
        m_count++;
        m_count++;
        m_count++;
        m_count++;
        m_count++;
        m_count++;
        m_count++;
        m_count++;
        m_count++;
        m_count++;
        m_count++;
        m_count++;
        m_count++;
        m_count++;
        m_count++;
        m_count++;
        m_count++;
        m_count++;
        m_count++;
        m_count++;
    }

    public int getCount()
    {
        return m_count + TestForNamespaceCycle.test();
    }
}
