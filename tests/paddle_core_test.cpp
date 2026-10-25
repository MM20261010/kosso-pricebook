// Host harness for the exact Android C++ pipeline; synthetic PPM files only, no image codecs.
#include "paddle_core.h"
#include <fstream>
#include <iostream>
#include <cstdint>
#include <opencv2/imgproc.hpp>
static std::vector<unsigned char> bytes(const std::string& path){std::ifstream f(path,std::ios::binary);if(!f)throw std::runtime_error("test file");return {std::istreambuf_iterator<char>(f),{}};}
static uint32_t u32(const std::vector<unsigned char>& b,size_t& p){if(p+4>b.size())throw std::runtime_error("dictionary");uint32_t n=b[p]|(b[p+1]<<8)|(b[p+2]<<16)|(b[p+3]<<24);p+=4;return n;}
int main(int argc,char** argv){try{
 if(argc!=3)return 2;std::string root=argv[1];localocr::Pipeline net;
 auto a=bytes(root+"/det.param"),b=bytes(root+"/det.bin"),c=bytes(root+"/rec.param"),d=bytes(root+"/rec.bin");a.push_back(0);c.push_back(0);
 if(net.detector.load_param_mem((const char*)a.data())||net.detector.load_model(b.data())!=b.size()||net.recognizer.load_param_mem((const char*)c.data())||net.recognizer.load_model(d.data())!=d.size())return 3;
 auto dict=bytes(root+"/keys.bin");size_t offset=0;auto count=u32(dict,offset);for(uint32_t i=0;i<count;i++){auto n=u32(dict,offset);if(offset+n>dict.size())return 4;net.keys.emplace_back((const char*)dict.data()+offset,n);offset+=n;}
 std::ifstream input(argv[2],std::ios::binary);std::string magic;int w,h,max;input>>magic>>w>>h>>max;input.get();if(magic!="P6"||w<2||h<2||(int64_t)w*h>4000000||max!=255)return 5;
 cv::Mat rgb(h,w,CV_8UC3);input.read((char*)rgb.data,w*h*3);if(input.gcount()!=w*h*3)return 6;cv::Mat bgr;cv::cvtColor(rgb,bgr,cv::COLOR_RGB2BGR);
 std::cout<<net.recognize(bgr);return 0;
}catch(...){return 7;}}
