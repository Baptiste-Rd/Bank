package org.junit;
public final class Assert {
  public static void assertEquals(Object e, Object a){ if(e==null? a!=null : !e.equals(a)) throw new AssertionError("expected:<"+e+"> but was:<"+a+">"); }
  public static void assertEquals(long e, long a){ if(e!=a) throw new AssertionError("expected:<"+e+"> but was:<"+a+">"); }
  public static void assertTrue(boolean c){ if(!c) throw new AssertionError("expected true"); }
  public static void assertFalse(boolean c){ if(c) throw new AssertionError("expected false"); }
  public static void assertNull(Object o){ if(o!=null) throw new AssertionError("expected null but was:<"+o+">"); }
}
