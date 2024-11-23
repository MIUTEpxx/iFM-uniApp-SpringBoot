package com.pxx.ifmserver.service.impl;


import com.pxx.ifmserver.entity.dto.Channel;
import com.pxx.ifmserver.entity.dto.Hashtag;
import com.pxx.ifmserver.entity.vo.ChannelVO;
import com.pxx.ifmserver.mapper.ChannelMapper;
import com.pxx.ifmserver.mapper.HashtagMapper;
import com.pxx.ifmserver.result.Result;
import com.pxx.ifmserver.service.ChannelService;
import com.pxx.ifmserver.utils.PictureUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ChannelServiceImpl implements ChannelService {
    private static final String CHANNEL_PICTURE_PATH="/resources/images/channel/";
    @Autowired
    private ChannelMapper channelMapper;
    @Autowired
    private HashtagMapper hashtagMapper;

    /**
     * 根据频道Id获取该频道信息
     * 其中频道基本信息和频道主题标签信息分贝获取再整合
     * @param channelId 频道id
     * @return 频道信息
     */
    @Override
    public Result getChannelByChannelId(Integer channelId){
        Map<String, Object> data = new HashMap<>();
        try {
            //获取频道基本信息
            Channel channel = channelMapper.getChannelByChannelId(channelId);
            if(channel == null){
                data.put("error", "id为 "+channelId+" 的频道不存在");
                return new Result(false,90000,"未找到频道",data);
            }
            //获取频道主题标签信息
            List<Hashtag> channelHashtag = channelMapper.listHashtagByChannelId(channelId);
            //整合 频道基本信息+频道主题标签信息
            ChannelVO channelVO = new ChannelVO();
            channelVO.setChannel(channel);
            channelVO.setChannelHashtag(channelHashtag);
            //将频道信息存入返回体的数据部分
            data.put("channel", channelVO);
            return Result.ok().data(data);
        }catch (DuplicateKeyException e){
            data.put("error", e.getMessage());
            return new Result(false,20001,"未知错误",data);
        }

    }

    /**
     * 根据用户id获取该用户创建的所有频道
     * 其中频道基本信息和频道主题标签信息分贝获取再整合
     * @param userId
     * @return
     */
    @Override
    public Result listChannelByUserId(Integer userId){
        Map<String, Object> data = new HashMap<>();
        try{
            //获取用户创建的所有频道信息列表
            List<Channel> channels = channelMapper.listChannelByUserId(userId);
            List<ChannelVO> channelVOLst = new ArrayList<>();
            //将频道信息存入返回体的数据部分
            for (Channel channel : channels) {
                //获取每个频道的主题标签
                List<Hashtag> channelHashtag = channelMapper.listHashtagByChannelId(channel.getChannelId());
                ChannelVO channelVO = new ChannelVO();
                //整合频道信息数据
                channelVO.setChannel(channel);
                //整合频道主题标签数据
                channelVO.setChannelHashtag(channelHashtag);
                channelVOLst.add(channelVO);
            }
            data.put("channelList", channelVOLst);
            return Result.ok().data(data);
        }catch (RuntimeException e){
            data.put("error", e.getMessage());
            return new Result(false,20001,"未知错误",data);
        }
    }

    /**
     * 创建新频道,将新频道记录保存在数据库表中,将封面图片文件保存在静态资源文件夹中
     * @param userId 创建频道的用户账号
     * @param channelTitle 频道标题
     * @param channelDetail 频道详情内容
     * @param channelPicture 频道封面图片文件数据
     * @param hashtagIdList 频道主题标签ID列表
     * @return 新生成的频道ID/错误信息
     */
    @Override
    public Result createChannel(Integer userId,String channelTitle, String channelDetail, MultipartFile channelPicture, List<Integer> hashtagIdList){
        Map<String, Object> data = new HashMap<>();
        Channel channel = new Channel();
        if (channelPicture.isEmpty()) {
            data.put("error","文件为空");
            Result result = new Result(false,70000,"文件上传失败",data);
            return result;
        }

        try{
            //根据标签id获取完整标签主题数据
            List<Hashtag> hashtagList = new ArrayList<>();
            for (Integer hashtagId : hashtagIdList) {
                Hashtag hashtag = hashtagMapper.getHashtagByHashId(hashtagId);
                hashtagList.add(hashtag);
            }
            //频道基本信息
            channel.setUserId(userId);
            channel.setChannelTitle(channelTitle);
            channel.setChannelDetail(channelDetail);
            //储存频道数据记录到表中,并获得频道id(自动保存在channel.channelId中)
            channelMapper.insertChannel(channel);
            //储存频道的主题标签到数据库表中
            for (Hashtag hashtag : hashtagList) {
                channelMapper.insertChannleHashtag(channel.getChannelId(), hashtag);
            }
            //储存频道封面图片并获得图片文件名称
            String pictureFileName;
            try {
                pictureFileName = PictureUtil.savePicture(channel.getChannelId(),channelPicture,CHANNEL_PICTURE_PATH);
            } catch (IOException e) {
                data.put("error.message", e.getMessage());
                data.put("channelId",channel.getChannelId());//返回频道ID
                return new Result(false,20001,"未知错误,封面图片上传失败",data);
            }
            //更新数据库中该频道的封面图片路径数据
            channelMapper.updateChannelPicurlByChannelId(channel.getChannelId(),"/images/channel/"+pictureFileName);
        }catch (RuntimeException e){
            //创建失败,删除频道数据记录
            channelMapper.deleteChannelHashtagByChannelId(channel.getChannelId());
            channelMapper.deleteChannelByChannelId(channel.getChannelId());
            data.put("error.message", e.getMessage());
            return new Result(false,20001,"未知错误",data);
        }
        data.put("channelId",channel.getChannelId());//返回频道ID
        return Result.ok().data(data);
    }

    /**
     * 储存新封面图片文件,删除旧封面图片文件,更新数据库记录
     * @param channelId 频道ID
     * @param channelPicture 新封面图片
     * @return
     */
    @Override
    public Result updateChannelPicture(Integer userId,Integer channelId, MultipartFile channelPicture){
        Map<String, Object> data = new HashMap<>();
        if (channelPicture.isEmpty()) {
            data.put("error","文件为空");
            Result result = new Result(false,70000,"文件上传失败",data);
            return result;
        }
        try{
            Channel channel = channelMapper.getChannelByChannelId(channelId);
            if(channel==null){
                data.put("error", "id为 "+channelId+" 的频道不存在");
                return new Result(false,90000,"修改失败,频道不存在",data);
            }
            if(!channel.getUserId().equals(userId)){
                //若频道作者ID不等于此用户ID,无权操作
                data.put("error","非频道创建者,无权修改ID为 "+channelId+" 的频道");
                return new Result(false,20002,"修改失败,无权操作",data);
            }
            //用于储存新封面图片文件名称
            String pictureFileName;
            //获取旧头像图片路径
            String oldPicture = channelMapper.getChannelByChannelId(channelId).getChannelPicture();
            try {
                //储存新图片文件并获取文件名称
                pictureFileName = PictureUtil.savePicture(channelId,channelPicture,CHANNEL_PICTURE_PATH);
                //删除旧图片
                PictureUtil.deletePicture(oldPicture);
            } catch (IOException e) {
                data.put("error.message", e.getMessage());
                return new Result(false,20001,"未知错误,封面图片上传失败",data);
            }
            //更新数据库中该频道的封面图片路径数据
            channelMapper.updateChannelPicurlByChannelId(channelId,"/images/channel/"+pictureFileName);
            data.put("channelPicture","/images/channel/"+pictureFileName);
        }catch (RuntimeException e){
            data.put("error.message", e.getMessage());
            return new Result(false,20001,"未知错误",data);
        }
        return Result.ok().data(data);
    }

    /**
     * 更改频道详情内容
     * @param userId
     * @param channelId
     * @param channelDetail
     * @return
     */
    @Override
    public Result updateChannelDetail(Integer userId,Integer channelId, String channelDetail){
        Map<String, Object> data = new HashMap<>();
        try{
            Channel channel = channelMapper.getChannelByChannelId(channelId);
            if(channel==null){
                data.put("error", "id为 "+channelId+" 的频道不存在");
                return new Result(false,90000,"修改失败,频道不存在",data);
            }
            if(!channel.getUserId().equals(userId)){
                //若频道作者ID不等于此用户ID,无权操作
                data.put("error","非频道创建者,无权修改ID为 "+channelId+" 的频道");
                return new Result(false,20002,"修改失败,无权操作",data);
            }
           channelMapper.updateChannelDetailByChannelId(channelId,channelDetail);
            data.put("channelDetail",channelDetail);
        }catch (RuntimeException e){
            data.put("error.message", e.getMessage());
            return new Result(false,20001,"未知错误",data);
        }
        return Result.ok().data(data);
    }

    /**
     * 更改频道标题
     * @param userId
     * @param channelId
     * @param channelTitle
     * @return
     */
    @Override
    public Result updateChannelTitle(Integer userId,Integer channelId, String channelTitle){
        Map<String, Object> data = new HashMap<>();
        try{
            Channel channel = channelMapper.getChannelByChannelId(channelId);
            if(channel==null){
                data.put("error", "id为 "+channelId+" 的频道不存在");
                return new Result(false,90000,"修改失败,频道不存在",data);
            }
            if(!channel.getUserId().equals(userId)){
                //若频道作者ID不等于此用户ID,无权操作
                data.put("error","非频道创建者,无权修改ID为 "+channelId+" 的频道");
                return new Result(false,20002,"修改失败,无权操作",data);
            }
           channelMapper.updateChannelTitleByChannelId(channelId,channelTitle);
            data.put("channelTitle",channelTitle);
        }catch (RuntimeException e){
            data.put("error.message", e.getMessage());
            return new Result(false,20001,"未知错误",data);
        }
        return Result.ok().data(data);
    }

    /**
     *  删除频道
     * @param userId
     * @param channelId
     * @return
     */
    @Override
    public Result deleteChannel(Integer userId,Integer channelId){
        Map<String, Object> data = new HashMap<>();
        try{
            Channel channel = channelMapper.getChannelByChannelId(channelId);
            if(channel==null){
                data.put("error", "id为 "+channelId+" 的频道不存在");
                return new Result(false,90000,"删除失败,频道不存在",data);
            }
            if(!channel.getUserId().equals(userId)){
                //若频道作者ID不等于此用户ID,无权操作
                data.put("error","非频道创建者,无权修改ID为 "+channelId+" 的频道");
                return new Result(false,20002,"删除失败,无权操作",data);
            }
            //删除频道记录
            channelMapper.deleteChannelByChannelId(channelId);
            //删除频道-主题标签记录
            channelMapper.deleteChannelHashtagByChannelId(channelId);
        }catch (RuntimeException e){
            data.put("error.message", e.getMessage());
            return new Result(false,20001,"未知错误",data);
        }
        return Result.ok();
    }

    /**
     * 获取今日热门频道
     * @return
     */
    @Override
    public Result listPopularChannel(){
        Map<String, Object> data = new HashMap<>();
        try {
            //获取36小时内更新过节目的频道
            List<Channel> channelList=channelMapper.listChannelsUpdatedWithin36HoursSortedBySubscribe();
            //将频道信息存入返回体的数据部分
            List<ChannelVO> channelVOLst = new ArrayList<>();
            for (Channel channel : channelList) {
                //获取每个频道的主题标签
                List<Hashtag> channelHashtag = channelMapper.listHashtagByChannelId(channel.getChannelId());
                ChannelVO channelVO = new ChannelVO();
                //整合频道信息数据
                channelVO.setChannel(channel);
                //整合频道主题标签数据
                channelVO.setChannelHashtag(channelHashtag);
                channelVOLst.add(channelVO);
            }
            data.put("channelList", channelVOLst);
        }catch (RuntimeException e){
            data.put("error.message", e.getMessage());
            return new Result(false,20001,"未知错误",data);
        }
        return Result.ok().data(data);
    }

    /**
     *  检查频道ID是否被用户ID订阅
     * @param userId
     * @param channelId
     * @return
     */
    @Override
    public Result checkChannelSubscriptionById(Integer userId,Integer channelId){
        Map<String, Object> data = new HashMap<>();
        try{
            //订阅信息储存于返回体
            data.put("subscription",channelMapper.checkChannelSubscriptionById(userId,channelId));
        }catch (RuntimeException e){
            data.put("error.message", e.getMessage());
            return new Result(false,20001,"未知错误",data);
        }
        return Result.ok().data(data);
    }

    /**
     * 用户 订阅频道/取消订阅(取决于用户是否已订阅)
     * @param userId
     * @param channelId
     * @return 订阅结果
     */
    @Override
    public Result changeSubscribe(Integer userId, Integer channelId){
        Map<String, Object> data = new HashMap<>();
        try{
            if(channelMapper.checkChannelSubscriptionById(userId,channelId)){
                //取消订阅,返回订阅结果:false
                channelMapper.deleteChannelSubscriptionById(userId,channelId);
                data.put("subscription",false);
            }else{
                //订阅频道,返回订阅结果:true
                channelMapper.insertChannelSubscription(userId,channelId);
                data.put("subscription",true);
            }
        }catch (RuntimeException e){
            data.put("error.message", e.getMessage());
            return new Result(false,20001,"未知错误",data);
        }
        return Result.ok().data(data);
    }

    /**
     *  获取用户订阅的频道信息
     * @param userId
     * @return
     */
    @Override
    public  Result listSubscribedChannel(Integer userId){
        Map<String, Object> data = new HashMap<>();
        try{
            //获取用户创建的所有频道信息列表
            List<Integer> channelIdList = channelMapper.listSubscribedChannelIdByUserId(userId);
            List<Channel> channelList=new ArrayList<>();
            for(Integer channelId:channelIdList){
                Channel channel = channelMapper.getChannelByChannelId(channelId);
                if(channel!=null){
                    channelList.add(channel);
                }
            }
            //将频道信息存入返回体的数据部分
            List<ChannelVO> channelVOLst = new ArrayList<>();
            for (Channel channel : channelList) {
                //获取每个频道的主题标签
                List<Hashtag> channelHashtag = channelMapper.listHashtagByChannelId(channel.getChannelId());
                ChannelVO channelVO = new ChannelVO();
                //整合频道信息数据
                channelVO.setChannel(channel);
                //整合频道主题标签数据
                channelVO.setChannelHashtag(channelHashtag);
                channelVOLst.add(channelVO);
            }
            data.put("channelList", channelVOLst);
            return Result.ok().data(data);
        }catch (RuntimeException e){
            data.put("error", e.getMessage());
            return new Result(false,20001,"未知错误",data);
        }
    }

    /**
     * 根据关键词搜索频道
     * @param keyWord
     * @return
     */
    @Override
    public Result searchChannel(String keyWord){
        Map<String, Object> data = new HashMap<>();
        try{
            //根据关键词获取频道数据
            List<Channel> channels = channelMapper.listChannelsByKeyWord(keyWord);
            //将频道信息存入返回体的数据部分
            List<ChannelVO> channelVOLst = new ArrayList<>();
            for (Channel channel : channels) {
                //获取每个频道的主题标签
                List<Hashtag> channelHashtag = channelMapper.listHashtagByChannelId(channel.getChannelId());
                ChannelVO channelVO = new ChannelVO();
                //整合频道信息数据
                channelVO.setChannel(channel);
                //整合频道主题标签数据
                channelVO.setChannelHashtag(channelHashtag);
                channelVOLst.add(channelVO);
            }
            data.put("channelList", channelVOLst);
            return Result.ok().data(data);
        }catch (RuntimeException e){
            data.put("error", e.getMessage());
            return new Result(false,20001,"未知错误",data);
        }
    }
}