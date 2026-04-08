public abstract class Entity{
    private final String id;

    protected Entity(String id){
        if (id == null || id.isBlank()){
            throw new IllegalArgumentException("id is required");
        }
        this.id = id.trim();
    }

    public String getId(){
        return id;
    }

    public abstract String printInfo();
}