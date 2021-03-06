package com.novocode.junit;

import java.util.HashMap;
import java.util.Map;

import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.scalatools.testing.EventHandler;
import org.scalatools.testing.Fingerprint;
import org.scalatools.testing.Logger;
import org.scalatools.testing.Runner2;


final class JUnitRunner extends Runner2
{
  private final ClassLoader testClassLoader;
  private final Logger[] loggers;
  private static final Object NULL = new Object();

  JUnitRunner(ClassLoader testClassLoader, Logger[] loggers)
  {
    this.testClassLoader = testClassLoader;
    this.loggers = loggers;
  }

  @Override
  public void run(String testClassName, Fingerprint fingerprint, EventHandler eventHandler, String [] args)
  {
    boolean quiet = false, verbose = false, nocolor = false;
    HashMap<String, String> sysprops = new HashMap<String, String>();
    String testFilter = "";
    for(String s : args)
    {
      if("-q".equals(s)) quiet = true;
      else if("-v".equals(s)) verbose = true;
      else if("-n".equals(s)) nocolor = true;
      else if(s.startsWith("-tests=")) testFilter = s.substring(7);
      else if(s.startsWith("-D") && s.contains("="))
      {
        int sep = s.indexOf('=');
        sysprops.put(s.substring(2, sep), s.substring(sep+1));
      }
    }
    for(String s : args)
    {
      if("+q".equals(s)) quiet = false;
      else if("+n".equals(s)) nocolor = false;
      else if("+v".equals(s)) verbose = false;
    }
    RichLogger logger = new RichLogger(loggers, !nocolor, testClassName);
    EventDispatcher ed = new EventDispatcher(logger, eventHandler, quiet, verbose);
    JUnitCore ju = new JUnitCore();
    ju.addListener(ed);

    HashMap<String, Object> oldprops = new HashMap<String, Object>();
    try
    {
      synchronized(System.getProperties())
      {
        for(Map.Entry<String, String> me : sysprops.entrySet())
        {
          String old = System.getProperty(me.getKey());
          oldprops.put(me.getKey(), old == null ? NULL : old);
        }
        for(Map.Entry<String, String> me : sysprops.entrySet())
          System.setProperty(me.getKey(), me.getValue());
      }
      try
      {
        Class<?> cl = testClassLoader.loadClass(testClassName);
        Request request = Request.classes(cl);
        if(testFilter.length() > 0) request = request.filterWith(new JUnitFilter(testFilter, ed));
        try { ju.run(request); } finally { ed.uncapture(true); }
      }
      catch(Exception ex) { ed.post(new TestExecutionFailedEvent(testClassName, ex)); }
    }
    finally
    {
      synchronized(System.getProperties())
      {
        for(Map.Entry<String, Object> me : oldprops.entrySet())
        {
          if(me.getValue() == NULL) System.clearProperty(me.getKey());
          else System.setProperty(me.getKey(), (String)me.getValue());
        }
      }
    }
  }
}
