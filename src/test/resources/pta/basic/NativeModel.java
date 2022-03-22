import java.security.AccessController;
import java.security.PrivilegedAction;

class NativeModel {

    public static void main(String[] args) throws Exception {
        objectClone();
        arraycopy();
        doPrivileged();
    }

    static void objectClone() throws Exception {
        A a = new A();
        Object o = a.callClone();
    }

    static void arraycopy() {
        Object[] src = new Object[5];
        src[0] = new Object();
        Object[] dest = new Object[5];
        System.arraycopy(src, 0, dest, 0, 5);
        Object o = dest[0];
    }

    static void doPrivileged() {
        A a = AccessController.doPrivileged(new PrivilegedAction<A>() {
            @Override
            public A run() {
                return new A();
            }
        });
    }
}

class A {
    Object callClone() throws CloneNotSupportedException {
        return clone();
    }
}
