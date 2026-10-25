#include "paddle_core.h"
#include <jni.h>
#include <android/asset_manager_jni.h>
#include <android/bitmap.h>
#include <opencv2/imgproc.hpp>
#include <stdexcept>
#include <memory>
namespace {
struct AssetDeleter{void operator()(AAsset* p){if(p)AAsset_close(p);}};
static std::vector<std::string> dictionary(AAssetManager* manager){
 std::unique_ptr<AAsset,AssetDeleter> asset(AAssetManager_open(manager,"paddle/keys.bin",AASSET_MODE_BUFFER));
 if(!asset||AAsset_getLength(asset.get())>512000)throw std::runtime_error("dictionary");
 auto read=[&](void* dst,size_t n){if(AAsset_read(asset.get(),dst,n)!=(int)n)throw std::runtime_error("dictionary read");};
 auto u32=[&](){unsigned char b[4];read(b,4);return (uint32_t)b[0]|((uint32_t)b[1]<<8)|((uint32_t)b[2]<<16)|((uint32_t)b[3]<<24);};
 if(u32()!=18385)throw std::runtime_error("dictionary count");std::vector<std::string> keys;
 for(int i=0;i<18385;i++){auto length=u32();if(length>64)throw std::runtime_error("dictionary length");std::string key(length,'\0');read(key.data(),length);keys.push_back(key);}return keys;
}
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_org_privateprice_pricebook_PaddleOcr_nativeRecognize(JNIEnv* env,jclass,jobject assets,jobject bitmap){
 try{
  AndroidBitmapInfo info{};if(AndroidBitmap_getInfo(env,bitmap,&info)!=ANDROID_BITMAP_RESULT_SUCCESS||info.format!=ANDROID_BITMAP_FORMAT_RGBA_8888||info.width<2||info.height<2||(uint64_t)info.width*info.height>4000000||info.stride<info.width*4)throw std::runtime_error("bitmap");
  void* pixels=nullptr;if(AndroidBitmap_lockPixels(env,bitmap,&pixels)!=ANDROID_BITMAP_RESULT_SUCCESS)throw std::runtime_error("bitmap lock");
  cv::Mat bgr;
  try{cv::Mat rgba(info.height,info.width,CV_8UC4,pixels,info.stride);cv::cvtColor(rgba,bgr,cv::COLOR_RGBA2BGR);}catch(...){AndroidBitmap_unlockPixels(env,bitmap);throw;}
  AndroidBitmap_unlockPixels(env,bitmap);
  auto manager=AAssetManager_fromJava(env,assets);if(!manager)throw std::runtime_error("assets");
  localocr::Pipeline pipeline;pipeline.keys=dictionary(manager);
  if(pipeline.detector.load_param(manager,"paddle/det.param")||pipeline.detector.load_model(manager,"paddle/det.bin")||pipeline.recognizer.load_param(manager,"paddle/rec.param")||pipeline.recognizer.load_model(manager,"paddle/rec.bin"))throw std::runtime_error("model load");
  auto text=localocr::encode(pipeline.recognizeDocument(bgr));jbyteArray out=env->NewByteArray((jsize)text.size());if(out)env->SetByteArrayRegion(out,0,(jsize)text.size(),(const jbyte*)text.data());return out;
 }catch(...){auto cls=env->FindClass("java/lang/IllegalStateException");if(cls)env->ThrowNew(cls,"PaddleOCRを実行できません。画像を小さくするか、レシートを分割して再選択してください。");return nullptr;}
}
