package com.h2m.alarm.application;

public class Singleton
{
    private static Singleton m_instance = null;

    static
    {
    }
    
    private Singleton()
    {
        super();
    }

    public static Singleton getInstance()
    {
        if (m_instance == null)
        {
            m_instance = new Singleton();
        }
        return m_instance;
    }
}