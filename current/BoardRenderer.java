
interface BoardRenderer {
    public void drawCell (Point p);
    public void showVerb (Point p,Point n,Particle oldSource,Particle oldTarget,UpdateEvent newPair);
}
