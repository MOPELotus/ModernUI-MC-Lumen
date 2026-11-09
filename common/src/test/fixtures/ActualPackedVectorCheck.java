import icyllis.modernui.text.*;
import icyllis.modernui.util.GrowingArrayUtils;
import java.util.*;
public final class ActualPackedVectorCheck {
 public static void main(String[] args) throws Exception {
   Random r=new Random(764177); long checks=0;
   for(int cols:new int[]{1,2,5}) {
     var iv=new PackedIntVector(cols); var ov=new PackedObjectVector<Object>(cols);
     var ir=new ArrayList<int[]>(); var or=new ArrayList<Object[]>();
     for(int i=0;i<3600;i++) {
       int op=i<300?0:r.nextInt(5);
       if(ir.isEmpty()||op==0) {
         int at=r.nextInt(ir.size()+1);int[] a=new int[cols];Object[] b=new Object[cols];
         for(int c=0;c<cols;c++){a[c]=r.nextInt();b[c]=new Object();}
         boolean zero=i%11==0;
         iv.insertAt(at,zero?null:a);ov.insertAt(at,zero?null:b);ir.add(at,zero?new int[cols]:a);or.add(at,zero?new Object[cols]:b);
       }else if(op==1){
         int at=r.nextInt(ir.size()+1),count=r.nextInt(ir.size()-at+1);
         iv.deleteAt(at,count);ov.deleteAt(at,count);ir.subList(at,at+count).clear();or.subList(at,at+count).clear();
       }else if(op==2){
         int at=r.nextInt(ir.size()),col=r.nextInt(cols),value=r.nextInt();Object obj=new Object();
         iv.setValue(at,col,value);ov.setValue(at,col,obj);ir.get(at)[col]=value;or.get(at)[col]=obj;
       }else if(op==3){
         int at=r.nextInt(ir.size()+1),col=r.nextInt(cols),delta=r.nextInt(100)-50;
         iv.adjustValuesBelow(at,col,delta);for(int row=at;row<ir.size();row++)ir.get(row)[col]+=delta;
       }else {
         iv.deleteAt(0,ir.size());ov.deleteAt(0,or.size());ir.clear();or.clear();
       }
       if(iv.size()!=ir.size()||ov.size()!=or.size())throw new AssertionError("size "+i);
       for(int row=0;row<ir.size();row++)for(int c=0;c<cols;c++){if(iv.getValue(row,c)!=ir.get(row)[c]||ov.getValue(row,c)!=or.get(row)[c])throw new AssertionError("rows "+i+"/"+row+"/"+c);checks++;}
     }
     ov.deleteAt(0,ov.size());
     var f=PackedObjectVector.class.getDeclaredField("mValues");f.setAccessible(true);var stored=(Object[])f.get(ov);
     if(Arrays.stream(stored).anyMatch(Objects::nonNull)) throw new AssertionError("empty object vector retains deleted references");
   }
   int[] a=new int[0];var expected=new ArrayList<Integer>();
   for(int i=0;i<3000;i++){
     int at=r.nextInt(i+1);a=GrowingArrayUtils.insert(a,i,at,i);expected.add(at,i);
     for(int j=0;j<=i;j++){if(a[j]!=expected.get(j))throw new AssertionError("array "+i+"/"+j);checks++;}
   }
   System.out.println("Pass "+checks+" differential value checks");
 }
}
