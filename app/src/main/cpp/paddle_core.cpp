// Local DB detection + CTC recognition. No file/network/logging APIs in this pipeline.
#include "paddle_core.h"
#include "clipper.hpp"
#include <opencv2/imgproc.hpp>
#include <opencv2/core/utils/logger.hpp>
#include <algorithm>
#include <cmath>
#include <stdexcept>
namespace localocr {
static constexpr int SIDE=960, REC_HEIGHT=48, MAX_BOXES=80;
Pipeline::Pipeline(){
 cv::setNumThreads(1);cv::utils::logging::setLogLevel(cv::utils::logging::LOG_LEVEL_SILENT);
 for(auto net:{&detector,&recognizer}){
  net->opt.num_threads=1;net->opt.lightmode=true;net->opt.use_vulkan_compute=false;
  net->opt.use_fp16_packed=false;net->opt.use_fp16_storage=false;net->opt.use_fp16_arithmetic=false;
  net->opt.use_bf16_storage=false;net->opt.use_int8_inference=false;
  // Bound transient convolution workspace on phones. Host accuracy is checked with this exact core.
  net->opt.use_winograd_convolution=false;net->opt.use_sgemm_convolution=false;
 }
}
static std::vector<cv::Point2f> mini(const cv::RotatedRect& rect){
 cv::Point2f p[4];rect.points(p);std::sort(p,p+4,[](auto a,auto b){return a.x<b.x;});
 auto a=p[0].y<p[1].y?p[0]:p[1],d=p[0].y<p[1].y?p[1]:p[0];
 auto b=p[2].y<p[3].y?p[2]:p[3],c=p[2].y<p[3].y?p[3]:p[2];return {a,b,c,d};
}
static float score(const cv::Mat& map,const std::vector<cv::Point2f>& box){
 int x0=map.cols-1,y0=map.rows-1,x1=0,y1=0;
 for(auto p:box){x0=std::min(x0,(int)std::floor(p.x));y0=std::min(y0,(int)std::floor(p.y));x1=std::max(x1,(int)std::ceil(p.x));y1=std::max(y1,(int)std::ceil(p.y));}
 x0=std::clamp(x0,0,map.cols-1);x1=std::clamp(x1,0,map.cols-1);y0=std::clamp(y0,0,map.rows-1);y1=std::clamp(y1,0,map.rows-1);
 if(x1<x0||y1<y0)return 0;
 cv::Mat mask=cv::Mat::zeros(y1-y0+1,x1-x0+1,CV_8U);std::vector<cv::Point> polygon;
 for(auto p:box)polygon.emplace_back((int)(p.x-x0),(int)(p.y-y0));
 cv::fillPoly(mask,std::vector<std::vector<cv::Point>>{polygon},cv::Scalar(1));
 return (float)cv::mean(map(cv::Rect(x0,y0,mask.cols,mask.rows)),mask)[0];
}
static ncnn::Mat input(const cv::Mat& im,int h,int w,bool detection){
 cv::Mat resized;cv::resize(im,resized,cv::Size(w,h),0,0,cv::INTER_LINEAR);
 ncnn::Mat x(SIDE,detection?SIDE:REC_HEIGHT,3);if(x.empty())throw std::bad_alloc();x.fill(0.f);
 const float mean[]={.485f,.456f,.406f},stdv[]={.229f,.224f,.225f};
 for(int c=0;c<3;c++){float* target=x.channel(c);
  for(int y=0;y<h;y++){const auto* row=resized.ptr<cv::Vec3b>(y);for(int z=0;z<w;z++)target[y*SIDE+z]=detection?(row[z][c]/255.f-mean[c])/stdv[c]:row[z][c]/127.5f-1.f;}
 }return x;
}
static std::string text(ncnn::Net& net,const cv::Mat& crop,const std::vector<std::string>& keys){
 cv::Mat oriented=crop;if(crop.rows/(float)crop.cols>=1.5f)cv::rotate(crop,oriented,cv::ROTATE_90_COUNTERCLOCKWISE);
 int width=std::clamp((int)std::ceil(REC_HEIGHT*(double)oriented.cols/oriented.rows),1,SIDE);
 ncnn::Mat x=input(oriented,REC_HEIGHT,width,false),out;auto ex=net.create_extractor();
 if(ex.input("in0",x)||ex.extract("out0",out)||out.dims!=2||out.w!=(int)keys.size()||out.h>960)throw std::runtime_error("recognizer shape");
 std::string result;int previous=-1;
 for(int t=0;t<out.h;t++){
  const float* p=out.row(t);int best=0;
  for(int c=0;c<out.w;c++){if(!std::isfinite(p[c]))throw std::runtime_error("nonfinite output");if(p[c]>p[best])best=c;}
  if(best!=0&&best!=previous)result+=keys[best];previous=best;
  if(result.size()>8192)throw std::runtime_error("line text limit");
 }return result;
}
std::string Pipeline::recognize(const cv::Mat& im){
 if(im.empty()||im.type()!=CV_8UC3||im.cols<2||im.rows<2||(int64_t)im.cols*im.rows>4000000)throw std::runtime_error("image limit");
 if(keys.size()!=18385)throw std::runtime_error("dictionary size");
 float ratio=SIDE/(float)std::max(im.cols,im.rows);
 int h=std::clamp((int)std::round(im.rows*ratio/32.f)*32,32,SIDE),w=std::clamp((int)std::round(im.cols*ratio/32.f)*32,32,SIDE);
 ncnn::Mat out;{ncnn::Mat x=input(im,h,w,true);auto ex=detector.create_extractor();
 if(ex.input("in0",x)||ex.extract("out0",out)||out.w!=SIDE||out.h!=SIDE||out.c!=1)throw std::runtime_error("detector shape");}
 cv::Mat map(h,w,CV_32F,(void*)out.data,SIDE*sizeof(float)),binary;
 cv::compare(map,.3,binary,cv::CMP_GT);std::vector<std::vector<cv::Point>> contours;cv::findContours(binary,contours,cv::RETR_LIST,cv::CHAIN_APPROX_SIMPLE);
 std::vector<Word> words;
 for(size_t i=0;i<std::min<size_t>(1000,contours.size());i++){
  if(contours[i].size()<3)continue;
  auto rect=cv::minAreaRect(contours[i]);if(std::min(rect.size.width,rect.size.height)<3)continue;auto box=mini(rect);
  if(score(map,box)<.6f)continue;
  double perimeter=cv::arcLength(box,true),area=std::abs(cv::contourArea(box));if(perimeter<=0||area<=0)continue;
  ClipperLib::Path path;for(auto p:box)path.emplace_back((ClipperLib::cInt)p.x,(ClipperLib::cInt)p.y);
  ClipperLib::ClipperOffset offset;offset.AddPath(path,ClipperLib::jtRound,ClipperLib::etClosedPolygon);ClipperLib::Paths expanded;offset.Execute(expanded,area*1.5/perimeter);
  if(expanded.size()!=1||expanded[0].size()<3)continue;
  std::vector<cv::Point2f> points;for(auto p:expanded[0])points.emplace_back((float)p.X,(float)p.Y);
  rect=cv::minAreaRect(points);if(std::min(rect.size.width,rect.size.height)<5)continue;box=mini(rect);
  for(auto& p:box){p.x=std::clamp(std::round(p.x/w*im.cols),0.f,(float)im.cols-1);p.y=std::clamp(std::round(p.y/h*im.rows),0.f,(float)im.rows-1);}
  int cw=(int)std::max(cv::norm(box[0]-box[1]),cv::norm(box[2]-box[3]));int ch=(int)std::max(cv::norm(box[0]-box[3]),cv::norm(box[1]-box[2]));
  if(cw<2||ch<2)continue;
  std::vector<cv::Point2f> dest={{0,0},{(float)cw,0},{(float)cw,(float)ch},{0,(float)ch}};cv::Mat crop;
  cv::warpPerspective(im,crop,cv::getPerspectiveTransform(box,dest),cv::Size(cw,ch),cv::INTER_CUBIC,cv::BORDER_REPLICATE);
  auto recognized=text(recognizer,crop,keys);if(!recognized.empty())words.push_back({box,recognized});
  if(words.size()>MAX_BOXES)throw std::runtime_error("too many text boxes; split receipt");
 }
 if(words.empty())return "";
 std::vector<float> angles;for(const auto& word:words)angles.push_back(std::atan2(word.box[1].y-word.box[0].y,word.box[1].x-word.box[0].x));
 std::sort(angles.begin(),angles.end());float angle=angles[angles.size()/2];
 struct Row{float y,height;std::vector<std::pair<float,std::string>> parts;};std::vector<Row> rows;
 for(const auto& word:words){
  cv::Point2f center;for(auto p:word.box)center+=p*.25f;
  float y=center.y*std::cos(angle)-center.x*std::sin(angle),height=std::max(cv::norm(word.box[0]-word.box[3]),cv::norm(word.box[1]-word.box[2]));
  int best=-1;float distance=1e9;
  for(size_t i=0;i<rows.size();i++)if(std::abs(rows[i].y-y)<.6f*std::min(rows[i].height,height)&&std::abs(rows[i].y-y)<distance){best=(int)i;distance=std::abs(rows[i].y-y);}
  if(best<0)rows.push_back({y,height,{{center.x,word.text}}});else{auto& row=rows[best];row.y=(row.y*row.parts.size()+y)/(row.parts.size()+1);row.parts.emplace_back(center.x,word.text);}
 }
 std::sort(rows.begin(),rows.end(),[](const auto& a,const auto& b){return a.y<b.y;});std::string result;
 for(auto& row:rows){std::sort(row.parts.begin(),row.parts.end());if(!result.empty())result+='\n';for(size_t i=0;i<row.parts.size();i++){if(i)result+=' ';result+=row.parts[i].second;}if(result.size()>128000)throw std::runtime_error("receipt text limit");}
 return result;
}
}
