package zoogas.core;

public abstract class Thunk<K,V>{
    public Thunk() {
        
    }

    // Not yet necessary?
    /*public Thunk(Object... members) {
        super();
        this.members = members;
    }
    Object[] members;*/

    public abstract V process(K input); // should this be variadic?
}