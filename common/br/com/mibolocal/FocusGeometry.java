package br.com.mibolocal;
import java.util.List;
public final class FocusGeometry {
 public static int choose(int[] from,List<int[]> targets,String direction){
  double x=(from[0]+from[2])/2.0,y=(from[1]+from[3])/2.0,best=Double.MAX_VALUE;int chosen=-1;
  for(int i=0;i<targets.size();i++){
   int[] r=targets.get(i);double dx=(r[0]+r[2])/2.0-x,dy=(r[1]+r[3])/2.0-y;
   boolean horizontal=direction.equals("LEFT")||direction.equals("RIGHT");
   double ahead=horizontal?dx:dy;if(direction.equals("LEFT")||direction.equals("UP"))ahead=-ahead;
   if(ahead<=1)continue;
   double across=Math.abs(horizontal?dy:dx);
   boolean aligned=horizontal?(r[1]<from[3]&&r[3]>from[1]):(r[0]<from[2]&&r[2]>from[0]);
   double score=ahead+across*3+(aligned?0:1000000);
   if(score<best){best=score;chosen=i;}
  }return chosen;
 }
}
