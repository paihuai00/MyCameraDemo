package com.cxs.mycamerademo.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.cxs.mycamerademo.ConvertUtils;
import com.cxs.mycamerademo.R;

import java.util.ArrayList;

/**
 * @author cuishuxiang
 * @date 2017/11/23.
 */

public class ImageRVAdadapter extends RecyclerView.Adapter<ImageRVAdadapter.ViewHolder> {
    private Context mContext;
    private ArrayList<Bitmap> bitmapList;

    public ImageRVAdadapter(Context mContext, ArrayList<Bitmap> bitmapList) {
        this.mContext = mContext;
        this.bitmapList = bitmapList;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = null;
        ViewHolder viewHolder = null;
        if (view == null || viewHolder == null) {
            view = LayoutInflater.from(mContext).inflate(R.layout.item_img_rv, parent, false);

            viewHolder = new ViewHolder(view);
        }

        return viewHolder;
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        Glide.with(mContext)
                .load(ConvertUtils.bitmap2Bytes(bitmapList.get(position), Bitmap.CompressFormat.JPEG))
                .error(R.mipmap.ic_launcher)
                .into(holder.show_img);

    }

    @Override
    public int getItemCount() {
        return bitmapList == null ? 0 : bitmapList.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        ImageView show_img;
        public ViewHolder(View itemView) {
            super(itemView);

            show_img = itemView.findViewById(R.id.show_img);
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (null != myRecycleItemClick) {
                        myRecycleItemClick.mItemClick(v,getAdapterPosition());
                    }
                }
            });
        }
    }

    private MyRecycleItemClick myRecycleItemClick;

    public void setMyRecycleItemClick(MyRecycleItemClick myRecycleItemClick) {
        this.myRecycleItemClick = myRecycleItemClick ;
    }

    public interface MyRecycleItemClick{
        public void mItemClick(View view, int position);
    }
}
