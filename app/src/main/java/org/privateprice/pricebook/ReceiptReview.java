package org.privateprice.pricebook;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/** In-memory proposals, explicit human confirmation, then existing DB/JSON values. No IO. */
final class ReceiptReview {
    enum Rounding { DOWN, NEAREST, UP }
    enum QuantityState { OCR, PROVISIONAL_ONE, USER_CONFIRMED, UNKNOWN }
    enum Risk { WEIGHT, DISCOUNT, OCR, RECONCILIATION }
    enum Choice { UNRESOLVED, SUBTOTAL, ITEM, DEFER }
    enum PriceMode { UNSET, GROSS_PRINTED, NET_PRINTED }
    static final class Item {
        final ReceiptParser.Candidate source;
        String name,specification;
        Long printedUnit; long lineAmount; ReceiptParser.AmountBasis basis; boolean detailsConfirmed;
        Integer quantity; QuantityState quantityState;
        Long grossUnit; Double taxRate,weightGrams;
        boolean selected,sale,codeConfirmed,exceptionConfirmed,deferred,estimationRejected,summaryAccepted=true;
        final EnumSet<Risk> risks=EnumSet.noneOf(Risk.class);
        Item(ReceiptParser.Candidate source){
            this.source=source;name=source.name;quantity=source.quantity;quantityState=quantity==null?QuantityState.UNKNOWN:QuantityState.OCR;
            printedUnit=source.unitPrice;lineAmount=source.price;basis=source.amountBasis;specification=source.specification;codeConfirmed=!source.codeUncertain;
            if(ReceiptParser.weightSignal(source.raw))risks.add(Risk.WEIGHT);
        }
        Long proposedUnit(){return quantity!=null&&quantity>0&&lineAmount%quantity==0?lineAmount/quantity:null;}
        String equation(){
            Long unit=printedUnit!=null?printedUnit:proposedUnit();
            String label=printedUnit!=null?"":quantityState==QuantityState.PROVISIONAL_ONE?"推定 ":"算出 ";
            return (unit==null?"単価 ?":label+unit+"円")+" × "+(quantity==null?"?":quantity)+"点 ＝ "+lineAmount+"円（"+ReceiptParser.basisLabel(basis)+"）";
        }
        String issue(){
            if(deferred)return "後で確認・今回保存しない";
            if(!summaryAccepted)return "集計か商品か確認";
            if(!exceptionConfirmed&&risks.contains(Risk.WEIGHT))return "重量・重量単価を確認";
            if(!exceptionConfirmed&&risks.contains(Risk.DISCOUNT))return "割引後の購入単価を確認";
            if(!exceptionConfirmed&&risks.contains(Risk.OCR))return "OCR・数量表記を確認";
            if(quantityState==QuantityState.PROVISIONAL_ONE)return "1点と推定・原票で確認";
            if(quantity==null)return "数量を確認";
            if(!codeConfirmed)return "先頭コードの分離を確認";
            if(!detailsConfirmed&&(source.uncertainLayout||source.nameSuffixUncertain))return "名称・位置を確認";
            if(printedUnit!=null&&printedUnit*quantity!=lineAmount&&!exceptionConfirmed)return "単価×数量が不一致";
            if(grossUnit==null)return basis==ReceiptParser.AmountBasis.EXCLUSIVE?"税込単価を確認":"価格の区分を確認";
            return "";
        }
        boolean special(){return risks.contains(Risk.WEIGHT)||risks.contains(Risk.DISCOUNT)||risks.contains(Risk.OCR);}
        boolean ready(){return !deferred&&summaryAccepted&&quantityState!=QuantityState.PROVISIONAL_ONE&&quantityState!=QuantityState.UNKNOWN
            &&(!special()||exceptionConfirmed)&&(!risks.contains(Risk.WEIGHT)||weightGrams!=null&&Double.isFinite(weightGrams)&&weightGrams>0&&weightGrams<=1_000_000)
            &&(printedUnit==null||quantity!=null&&printedUnit*quantity==lineAmount||exceptionConfirmed)&&codeConfirmed&&name!=null&&!name.trim().isEmpty()&&name.trim().length()<=120&&quantity!=null&&quantity>=1&&quantity<=999&&grossUnit!=null&&grossUnit>=1&&grossUnit<=1_000_000
            &&(taxRate==null||Double.isFinite(taxRate)&&taxRate>=0&&taxRate<=100);}
        Item copy(){Item n=new Item(source);n.adopt(this);return n;}
        void adopt(Item n){name=n.name;specification=n.specification;quantity=n.quantity;quantityState=n.quantityState;printedUnit=n.printedUnit;lineAmount=n.lineAmount;basis=n.basis;detailsConfirmed=n.detailsConfirmed;grossUnit=n.grossUnit;taxRate=n.taxRate;weightGrams=n.weightGrams;selected=n.selected;sale=n.sale;codeConfirmed=n.codeConfirmed;exceptionConfirmed=n.exceptionConfirmed;deferred=n.deferred;estimationRejected=n.estimationRejected;summaryAccepted=n.summaryAccepted;risks.clear();risks.addAll(n.risks);}
    }
    static final class Held {
        final ReceiptParser.HeldItem source;final Item item;Choice choice=Choice.UNRESOLVED;
        Held(ReceiptParser.HeldItem source){this.source=source;item=new Item(source.item);item.summaryAccepted=false;}
    }
    static final class Draft {
        final Item item;final Held held;
        String quantity,gross,tax="",weight="";boolean defer,exceptionChecked;final EnumSet<Risk> declaredRisks=EnumSet.noneOf(Risk.class);
        Choice choice;
        Draft(Item item,Held held){this.item=item;this.held=held;declaredRisks.addAll(item.risks);quantity=item.quantity==null?"":item.quantity.toString();gross=item.grossUnit==null?"":item.grossUnit.toString();weight=item.weightGrams==null?"":item.weightGrams.toString();defer=item.deferred;exceptionChecked=item.exceptionConfirmed;tax=item.taxRate==null?"":item.taxRate.toString();choice=held==null?Choice.ITEM:held.choice;}
    }
    static final class Batch {
        final List<Draft> rows=new ArrayList<>();
        boolean acknowledged,acceptOneEstimates,acceptCodes,acceptMismatch;
        PriceMode mode=PriceMode.UNSET;String commonTax="";Rounding rounding;
        boolean confirmSummary;String subtotal="",taxAmount="",total="",initialSubtotal="",initialTax="",initialTotal="";
        ReceiptParser.AmountBasis summaryBasis=ReceiptParser.AmountBasis.UNKNOWN,initialBasis=ReceiptParser.AmountBasis.UNKNOWN;
        boolean summaryChanged(){return !subtotal.trim().equals(initialSubtotal)||!taxAmount.trim().equals(initialTax)||!total.trim().equals(initialTotal)||summaryBasis!=initialBasis;}
        boolean hasDataChanges(){
            if(mode!=PriceMode.UNSET||!commonTax.isBlank()||rounding!=null||confirmSummary||summaryChanged())return true;
            for(Draft d:rows){Item i=d.item;if(d.held!=null&&d.choice!=d.held.choice||!d.quantity.trim().equals(i.quantity==null?"":i.quantity.toString())
                ||!d.gross.trim().equals(i.grossUnit==null?"":i.grossUnit.toString())||!d.tax.trim().equals(i.taxRate==null?"":i.taxRate.toString())
                ||!d.weight.trim().equals(i.weightGrams==null?"":i.weightGrams.toString())||d.defer!=i.deferred||d.exceptionChecked!=i.exceptionConfirmed||!d.declaredRisks.equals(i.risks))return true;}
            return false;
        }
    }
    final ReceiptParser.Receipt receipt;
    final List<Item> items=new ArrayList<>();final List<Held> held=new ArrayList<>();
    String store,date;boolean showQuestions=true,mismatchAccepted;Batch batch;
    private boolean summaryEdited;private Long confirmedSubtotal,confirmedTax,confirmedTotal;
    private ReceiptParser.AmountBasis confirmedBasis;
    ReceiptReview(ReceiptParser.Receipt receipt){
        this.receipt=receipt;store=receipt.store;date=receipt.date;
        for(ReceiptParser.Candidate c:receipt.items)items.add(new Item(c));
        for(ReceiptParser.HeldItem h:receipt.heldItems)held.add(new Held(h));
        boolean mismatch=initialMismatch();
        for(int index=0;index<items.size();index++){
            Item i=items.get(index);
            if(receipt.hasDiscount||i.source.raw.matches("(?s).*(?:まとめ|よりどり|組合|セット価格|[0-9]+(?:点|個)で|値引|割引|クーポン).*"))i.risks.add(Risk.DISCOUNT);
            if(i.source.uncertainLayout||i.name.matches(".*[?□�].*"))i.risks.add(Risk.OCR);
            for(ReceiptParser.Decision d:receipt.decisions){
                boolean linked=d.candidates.contains(index+1)||d.audit!=null&&Integer.valueOf(index+1).equals(d.audit.attemptedCandidate);
                if(linked&&d.audit!=null&&(d.audit.association==ReceiptDiagnostics.Association.AMOUNT_MISMATCH||d.audit.association==ReceiptDiagnostics.Association.FIELD_CONFLICT||d.audit.association==ReceiptDiagnostics.Association.GEOMETRY_REJECTED||d.audit.association==ReceiptDiagnostics.Association.BARE_COUNT_UNPROVEN||i.source.quantity==null&&(d.audit.recognizedRegions==ReceiptDiagnostics.Token.PARTIAL_MARKER||d.audit.assembledRow==ReceiptDiagnostics.Token.PARTIAL_MARKER)))i.risks.add(Risk.OCR);
                if(d.audit!=null&&d.kind==ReceiptParser.Kind.QUANTITY&&d.candidates.isEmpty()){
                    for(ReceiptParser.Decision own:ReceiptParser.decisionsForCandidate(receipt,index+1))if(own.kind==ReceiptParser.Kind.ITEM&&Math.abs(own.row-d.row)==1
                        &&(d.audit.recognizedRegions!=ReceiptDiagnostics.Token.NONE_OBSERVED||d.audit.assembledRow!=ReceiptDiagnostics.Token.NONE_OBSERVED))i.risks.add(Risk.OCR);
                }
            }
            if(mismatch)i.risks.add(Risk.RECONCILIATION);
            if(i.quantity==null&&i.printedUnit==null&&!i.special()&&!mismatch){i.quantity=1;i.quantityState=QuantityState.PROVISIONAL_ONE;}
        }
    }
    private boolean initialMismatch(){
        Integer hint=receipt.printedItemCount;
        for(Held h:held)if(h.choice==Choice.SUBTOTAL||h.choice==Choice.UNRESOLVED){Integer n=h.source.countHint;if(n!=null){if(hint!=null&&!hint.equals(n))return true;hint=n;}}
        int minimum=receipt.knownQuantitySum()+receipt.unknownQuantityCount();
        Integer bandCount=ReceiptFiscal.bandCount(receipt);
        boolean mixed=receipt.items.stream().anyMatch(i->i.amountBasis!=ReceiptParser.AmountBasis.UNKNOWN&&receipt.amountBasis!=ReceiptParser.AmountBasis.UNKNOWN&&i.amountBasis!=receipt.amountBasis);
        return mixed||bandCount!=null&&bandCount!=minimum||ReceiptFiscal.numericConflict(receipt,receipt.itemSum())||hint!=null&&minimum!=hint||receipt.subtotal!=null&&receipt.subtotal!=receipt.itemSum()
            ||receipt.total!=null&&(receipt.amountBasis==ReceiptParser.AmountBasis.INCLUSIVE&&receipt.total!=receipt.itemSum()
            ||receipt.amountBasis==ReceiptParser.AmountBasis.EXCLUSIVE&&receipt.taxAmount!=null&&receipt.itemSum()+receipt.taxAmount!=receipt.total);
    }
    Batch batch(){
        if(batch==null){batch=new Batch();for(Item i:items)if((!i.ready()||i.deferred)&&held.stream().noneMatch(h->h.item==i))batch.rows.add(new Draft(i,null));for(Held h:held)batch.rows.add(new Draft(h.item,h));
            ReceiptParser.Receipt a=aggregate();Long sub=a.subtotal,total=a.total;for(Held h:held)if(h.choice==Choice.UNRESOLVED){if(h.source.aggregateKind==ReceiptParser.Kind.TOTAL&&total==null)total=h.item.lineAmount;if(h.source.aggregateKind==ReceiptParser.Kind.SUBTOTAL&&sub==null)sub=h.item.lineAmount;}
            batch.subtotal=batch.initialSubtotal=value(sub);batch.taxAmount=batch.initialTax=value(a.taxAmount);batch.total=batch.initialTotal=value(total);batch.summaryBasis=batch.initialBasis=a.amountBasis==ReceiptParser.AmountBasis.UNKNOWN?a.basisProposal:a.amountBasis;}
        return batch;
    }
    private static String value(Long n){return n==null?"":n.toString();}
    private static Long summaryAmount(String s){if(s.trim().isEmpty())return null;long n=Long.parseLong(s.trim());if(n<0||n>1_000_000)throw new IllegalArgumentException("集計金額は0〜100万円です");return n;}
    void rejectEstimate(Item item){if(item.quantityState==QuantityState.PROVISIONAL_ONE){item.quantity=null;item.quantityState=QuantityState.UNKNOWN;item.estimationRejected=true;item.grossUnit=null;item.selected=false;if(batch!=null)for(Draft d:batch.rows)if(d.item==item)d.quantity="";}}
    void edited(Item item,boolean confirmQuantity){if(item.quantity==null)item.quantityState=QuantityState.UNKNOWN;else if(confirmQuantity)item.quantityState=QuantityState.USER_CONFIRMED;
        if(batch!=null){batch.acknowledged=false;batch.acceptMismatch=false;}
        if(batch!=null)for(Draft d:batch.rows)if(d.item==item){d.quantity=item.quantity==null?"":item.quantity.toString();d.gross=item.grossUnit==null?"":item.grossUnit.toString();d.weight=item.weightGrams==null?"":item.weightGrams.toString();d.tax=item.taxRate==null?"":item.taxRate.toString();d.exceptionChecked=item.exceptionConfirmed;d.defer=item.deferred;}
        mismatchAccepted=false;
    }
    static Long amount(String s){if(s==null||s.trim().isEmpty())return null;long n=Long.parseLong(s.trim());if(n<1||n>1_000_000)throw new IllegalArgumentException("金額は1〜100万円です");return n;}
    private static Integer quantity(String s){if(s==null||s.trim().isEmpty())return null;int n=Integer.parseInt(s.trim());if(n<1||n>999)throw new IllegalArgumentException("数量は1〜999点です");return n;}
    private static Double rate(String s){if(s.trim().isEmpty())return null;double n=Double.parseDouble(s.trim());if(!Double.isFinite(n)||n<0||n>100)throw new IllegalArgumentException("税率を確認してください");return n;}
    /** All rows validate before any live item, selection, or aggregate is updated. */
    void apply(Batch b){
        if(!b.acknowledged)throw new IllegalArgumentException("選択行を原票で確認してください");
        if(b.summaryChanged()&&!b.confirmSummary)throw new IllegalArgumentException("訂正した集計金額を原票で確認してください");
        if(b.confirmSummary){summaryAmount(b.subtotal);summaryAmount(b.taxAmount);summaryAmount(b.total);}
        List<Item> originals=new ArrayList<>(),copies=new ArrayList<>();
        for(Draft d:b.rows){
            if(d.held!=null&&d.choice==Choice.UNRESOLVED)throw new IllegalArgumentException("集計か商品か、または後で確認を選んでください");
            Item n=d.item.copy();n.risks.addAll(d.declaredRisks);
            if(d.held==null&&d.item.ready()){originals.add(d.item);copies.add(n);continue;}
            if(d.held!=null&&d.choice!=Choice.ITEM){n.selected=false;n.summaryAccepted=false;originals.add(d.item);copies.add(n);continue;}
            n.summaryAccepted=true;n.deferred=d.defer;
            if(d.defer){n.selected=false;originals.add(d.item);copies.add(n);continue;}
            Integer q=quantity(d.quantity);if(q==null)throw new IllegalArgumentException("数量を入力するか後で確認を選んでください");
            if(d.item.quantityState==QuantityState.PROVISIONAL_ONE&&q==1&&!n.special()&&!b.acceptOneEstimates)throw new IllegalArgumentException("1点の推定を確認するか数量を修正してください");
            n.quantity=q;n.quantityState=QuantityState.USER_CONFIRMED;
            if(!n.codeConfirmed&&!b.acceptCodes)throw new IllegalArgumentException("先頭コードの分離後の名称を確認してください");
            n.codeConfirmed=true;n.detailsConfirmed=true;n.taxRate=rate(d.tax.isBlank()?b.commonTax:d.tax);
            if(n.source.taxRate!=null&&n.taxRate!=null&&n.source.taxRate.doubleValue()!=n.taxRate&&!d.exceptionChecked)throw new IllegalArgumentException("OCR税率と異なります。個別確認に印を付けてください");
            Long gross=amount(d.gross);
            if(n.special()){
                if(!d.exceptionChecked||gross==null)throw new IllegalArgumentException("重量・割引・OCR不確実行は個別確認と税込購入単価の入力が必要です");
                n.exceptionConfirmed=true;n.sale|=n.risks.contains(Risk.DISCOUNT);
                if(n.risks.contains(Risk.WEIGHT)){double grams=Double.parseDouble(d.weight);if(!Double.isFinite(grams)||grams<=0||grams>1_000_000)throw new IllegalArgumentException("購入重量gを確認してください");n.weightGrams=grams;}
            }
            if(gross==null){
                if(b.mode==PriceMode.UNSET)throw new IllegalArgumentException("印字金額の区分を選ぶか税込単価を入力してください");
                gross=confirmedUnit(n,b.mode==PriceMode.NET_PRINTED,n.taxRate,b.rounding);
            }
            if(n.basis==ReceiptParser.AmountBasis.UNKNOWN&&b.mode!=PriceMode.UNSET)n.basis=b.mode==PriceMode.NET_PRINTED?ReceiptParser.AmountBasis.EXCLUSIVE:ReceiptParser.AmountBasis.INCLUSIVE;
            n.grossUnit=gross;n.selected=true;
            if(!n.ready())throw new IllegalArgumentException("未確認の行があります");originals.add(d.item);copies.add(n);
        }
        List<Item> future=new ArrayList<>();for(Item i:items){int at=originals.indexOf(i);boolean remove=false;for(Draft d:b.rows)if(d.item==i&&d.held!=null&&d.choice!=Choice.ITEM)remove=true;if(!remove)future.add(at<0?i:copies.get(at));}
        for(int at=0;at<b.rows.size();at++){Draft d=b.rows.get(at);if(d.held!=null&&d.choice==Choice.ITEM&&!items.contains(d.item))future.add(copies.get(at));}
        ReceiptParser.Receipt summary=aggregate(b);
        boolean keepPriorMismatch=mismatchAccepted&&!b.hasDataChanges();
        if(mismatch(future,summary)&&!b.acceptMismatch&&!keepPriorMismatch)throw new IllegalArgumentException("点数・明細・集計が不一致です。原票を見直すか不一致確認に印を付けてください");
        for(int at=0;at<originals.size();at++)originals.get(at).adopt(copies.get(at));
        for(Draft d:b.rows)if(d.held!=null){d.held.choice=d.choice;if(d.choice==Choice.ITEM&&!items.contains(d.item))items.add(d.item);else if(d.choice!=Choice.ITEM)items.remove(d.item);}
        if(b.confirmSummary||summaryEdited){summaryEdited=true;confirmedSubtotal=summary.subtotal;confirmedTax=summary.taxAmount;confirmedTotal=summary.total;confirmedBasis=summary.amountBasis;}
        mismatchAccepted=b.acceptMismatch||keepPriorMismatch;batch=null;showQuestions=false;
    }
    private ReceiptParser.Receipt aggregate(Batch proposed){
        ReceiptParser.Receipt n=new ReceiptParser.Receipt();n.store=store;n.date=date;n.total=receipt.total;n.subtotal=receipt.subtotal;n.taxAmount=receipt.taxAmount;n.amountBasis=receipt.amountBasis;n.includesTax=receipt.includesTax;n.hasDiscount=receipt.hasDiscount||items.stream().anyMatch(i->i.risks.contains(Risk.DISCOUNT));n.totalUncertain=receipt.totalUncertain;n.subtotalUncertain=receipt.subtotalUncertain||mismatchAccepted||items.stream().anyMatch(i->i.exceptionConfirmed&&(i.risks.contains(Risk.OCR)||i.printedUnit!=null&&i.quantity!=null&&i.printedUnit*i.quantity!=i.lineAmount));n.printedItemCount=receipt.printedItemCount;
        n.basisProposal=receipt.basisProposal;n.basisSource=receipt.basisSource;n.basisConflict=receipt.basisConflict;if(items.stream().mapToLong(i->i.lineAmount).sum()!=receipt.itemSum()||items.stream().anyMatch(i->i.basis!=ReceiptParser.AmountBasis.UNKNOWN&&i.basis!=receipt.basisProposal))n.basisProposal=ReceiptParser.AmountBasis.UNKNOWN;
        n.taxBreakdowns.addAll(receipt.taxBreakdowns);n.taxConflict=receipt.taxConflict;n.taxAmountUncertain=receipt.taxAmountUncertain;n.taxDetailsPending=receipt.taxDetailsPending;n.taxAmountFromBreakdown=receipt.taxAmountFromBreakdown;
        n.totalCandidates.addAll(receipt.totalCandidates);
        for(Held h:held)if(h.source.provisionalAggregate){Choice choice=h.choice;if(proposed!=null)for(Draft d:proposed.rows)if(d.held==h)choice=d.choice;
            if(choice!=Choice.SUBTOTAL){if(h.source.aggregateKind==ReceiptParser.Kind.TOTAL){if(n.total!=null&&n.total==h.source.item.price)n.total=null;n.totalUncertain=true;}else{if(n.subtotal!=null&&n.subtotal==h.source.item.price)n.subtotal=null;if(!receipt.countFromExplicitTotal&&h.source.countHint!=null&&h.source.countHint.equals(n.printedItemCount))n.printedItemCount=null;n.subtotalUncertain=true;}}
        }
        for(Held h:held){Choice c=h.choice;if(proposed!=null)for(Draft d:proposed.rows)if(d.held==h)c=d.choice;
            if(c==Choice.SUBTOTAL){if(h.source.aggregateKind==ReceiptParser.Kind.TOTAL){if(n.total!=null&&n.total!=h.item.lineAmount)throw new IllegalArgumentException("合計候補の選択が競合しています");n.total=h.item.lineAmount;n.totalUncertain=false;}else{if(n.subtotal!=null&&n.subtotal!=h.item.lineAmount)throw new IllegalArgumentException("集計候補の選択が競合しています");n.subtotal=h.item.lineAmount;n.subtotalUncertain=true;if(h.source.countHint!=null)n.printedItemCount=h.source.countHint;}}
            if(c==Choice.DEFER||c==Choice.UNRESOLVED){if(h.source.aggregateKind==ReceiptParser.Kind.TOTAL)n.totalUncertain=true;else n.subtotalUncertain=true;}
        }
        boolean editing=proposed!=null&&proposed.confirmSummary;
        if(editing||summaryEdited){n.subtotal=editing?summaryAmount(proposed.subtotal):confirmedSubtotal;n.taxAmount=editing?summaryAmount(proposed.taxAmount):confirmedTax;n.total=editing?summaryAmount(proposed.total):confirmedTotal;n.amountBasis=editing?proposed.summaryBasis:confirmedBasis;n.includesTax=n.amountBasis==ReceiptParser.AmountBasis.INCLUSIVE;n.basisProposal=ReceiptParser.AmountBasis.UNKNOWN;n.basisSource=ReceiptParser.ValueSource.USER_CONFIRMED;n.basisConflict=false;n.subtotalUncertain=false;n.totalUncertain=false;n.taxAmountUncertain=false;n.taxAmountFromBreakdown=false;}
        if(proposed!=null&&summaryEdited&&!editing)for(Draft d:proposed.rows)if(d.held!=null&&d.choice!=d.held.choice){
            boolean total=d.held.source.aggregateKind==ReceiptParser.Kind.TOTAL;
            if(d.choice==Choice.SUBTOTAL){if(total)n.total=d.item.lineAmount;else n.subtotal=d.item.lineAmount;}
            else if(d.held.choice==Choice.SUBTOTAL){if(total&&receipt.total==null&&n.total!=null&&n.total==d.item.lineAmount)n.total=null;else if(!total&&n.subtotal!=null&&n.subtotal==d.item.lineAmount)n.subtotal=null;}
        }
        n.subtotalUncertain|=mismatchAccepted;
        return n;
    }
    private boolean mismatch(List<Item> items,ReceiptParser.Receipt summary){
        if(items.stream().anyMatch(i->i.basis!=ReceiptParser.AmountBasis.UNKNOWN&&summary.amountBasis!=ReceiptParser.AmountBasis.UNKNOWN&&i.basis!=summary.amountBasis))return true;
        long sum=0;int count=0;boolean known=true;for(Item i:items){sum+=i.lineAmount;if(i.quantity==null)known=false;else count+=i.quantity;}
        boolean priceMismatch=false,fullGross=true;long paidSum=0;
        for(Item i:items){if(i.grossUnit==null||i.quantity==null||i.deferred)fullGross=false;else paidSum+=i.grossUnit*i.quantity;}
        if(fullGross&&!summary.hasDiscount&&summary.total!=null&&paidSum!=summary.total)priceMismatch=true;
        for(Item i:items)if(i.grossUnit!=null&&i.quantity!=null&&!i.exceptionConfirmed){
            if(i.basis==ReceiptParser.AmountBasis.INCLUSIVE&&i.grossUnit*i.quantity!=i.lineAmount)priceMismatch=true;
            if(i.basis==ReceiptParser.AmountBasis.EXCLUSIVE&&i.taxRate!=null&&i.lineAmount%i.quantity==0){
                java.math.BigDecimal expected=java.math.BigDecimal.valueOf(i.lineAmount/i.quantity).multiply(java.math.BigDecimal.ONE.add(java.math.BigDecimal.valueOf(i.taxRate).movePointLeft(2)));
                priceMismatch|=i.grossUnit<expected.setScale(0,java.math.RoundingMode.FLOOR).longValueExact()||i.grossUnit>expected.setScale(0,java.math.RoundingMode.CEILING).longValueExact();
            }
        }
        boolean countConflict=receipt.countFromExplicitTotal&&receipt.printedItemCount!=null&&summary.printedItemCount!=null&&!receipt.printedItemCount.equals(summary.printedItemCount);
        return ReceiptFiscal.numericConflict(summary,sum)||priceMismatch||countConflict||summary.printedItemCount!=null&&(count>summary.printedItemCount||known&&count!=summary.printedItemCount)
            ||summary.subtotal!=null&&sum!=summary.subtotal
            ||!summary.hasDiscount&&summary.total!=null&&(summary.amountBasis==ReceiptParser.AmountBasis.INCLUSIVE&&sum!=summary.total
                ||summary.amountBasis==ReceiptParser.AmountBasis.EXCLUSIVE&&summary.taxAmount!=null&&sum+summary.taxAmount!=summary.total);
    }
    ReceiptParser.Receipt aggregate(){return aggregate(null);}
    private String fiscalValue(ReceiptParser.Receipt a,ReceiptParser.Kind kind){
        Long amount=kind==ReceiptParser.Kind.SUBTOTAL?a.subtotal:a.total;
        boolean pending=kind==ReceiptParser.Kind.SUBTOTAL?a.subtotalUncertain:a.totalUncertain;
        if(kind==ReceiptParser.Kind.TOTAL&&!summaryEdited){java.util.LinkedHashSet<Long> alternatives=new java.util.LinkedHashSet<>();if(amount!=null)alternatives.add(amount);for(Held h:held)if(h.source.aggregateKind==kind&&h.choice==Choice.UNRESOLVED)alternatives.add(h.item.lineAmount);alternatives.addAll(a.totalCandidates);if(alternatives.size()>1)return "要確認（合計候補 "+new ArrayList<>(alternatives).subList(0,Math.min(3,alternatives.size()))+"円）";}
        for(Held h:held)if(h.source.aggregateKind==kind){if(h.choice==Choice.SUBTOTAL&&!summaryEdited)pending=false;else if(h.choice==Choice.UNRESOLVED&&amount==null)return h.item.lineAmount+"円（要確認）";}
        if(kind==ReceiptParser.Kind.TOTAL&&amount==null&&!summaryEdited&&!a.totalCandidates.isEmpty())return "要確認（合計候補 "+a.totalCandidates.subList(0,Math.min(3,a.totalCandidates.size()))+"円）";
        return amount==null?"未取得":amount+"円"+(pending?"（要確認）":"");
    }
    String fiscalStatus(){ReceiptParser.Receipt a=aggregate();long sum=items.stream().mapToLong(i->i.lineAmount).sum();
        boolean unresolved=held.stream().anyMatch(h->h.choice==Choice.UNRESOLVED);
        ReceiptParser.Receipt checked=aggregate();if(!unresolved&&held.stream().anyMatch(h->h.source.aggregateKind==ReceiptParser.Kind.SUBTOTAL&&h.choice==Choice.SUBTOTAL))checked.subtotalUncertain=false;
        ReceiptFiscal.Match match=ReceiptFiscal.match(checked,sum,unresolved);
        if(mismatch(items,a))match=ReceiptFiscal.Match.MISMATCH;
        ReceiptParser.Receipt preview=aggregate();
        for(ReceiptParser.Kind kind:new ReceiptParser.Kind[]{ReceiptParser.Kind.SUBTOTAL,ReceiptParser.Kind.TOTAL}){
            java.util.LinkedHashSet<Long> candidates=new java.util.LinkedHashSet<>();for(Held h:held)if(h.source.aggregateKind==kind&&h.choice==Choice.UNRESOLVED)candidates.add(h.item.lineAmount);
            if(kind==ReceiptParser.Kind.TOTAL)candidates.addAll(preview.totalCandidates);
            if(candidates.size()==1){if(kind==ReceiptParser.Kind.TOTAL&&preview.total==null)preview.total=candidates.iterator().next();if(kind==ReceiptParser.Kind.SUBTOTAL&&preview.subtotal==null)preview.subtotal=candidates.iterator().next();}
        }
        ReceiptFiscal.Match relations=ReceiptFiscalNotation.relations(preview,sum);
        String basis=ReceiptParser.basisLabel(a.amountBasis);if(a.amountBasis==ReceiptParser.AmountBasis.UNKNOWN&&a.basisProposal!=ReceiptParser.AmountBasis.UNKNOWN)basis=ReceiptParser.basisLabel(a.basisProposal)+"候補・要確認";else if(a.basisSource==ReceiptParser.ValueSource.USER_CONFIRMED)basis+="・本人確認";
        boolean haveBases=!a.taxBreakdowns.isEmpty()&&a.taxBreakdowns.stream().allMatch(b->b.base!=null&&b.baseAlternatives.isEmpty());long baseSum=0;for(ReceiptParser.TaxBreakdown b:a.taxBreakdowns)if(b.base!=null)baseSum+=b.base;
        String baseStatus=haveBases?baseSum+"円"+(a.taxBreakdowns.stream().anyMatch(b->b.uncertain||b.baseSource==ReceiptParser.ValueSource.PROPOSED)?"（要確認）":""):a.taxBreakdowns.stream().anyMatch(b->!b.baseAlternatives.isEmpty())?"要確認（候補が複数）":a.taxBreakdowns.stream().anyMatch(b->b.base!=null)?"一部取得・要確認":"未取得";
        return "商品行金額計："+sum+"円（"+basis+"）\n税率別対象額計："+baseStatus+"\nレシート小計："+fiscalValue(a,ReceiptParser.Kind.SUBTOTAL)
            +"\n税額合計："+(a.taxAmount==null?(a.taxAmountUncertain?"要確認（税額候補が競合）":a.taxDetailsPending?"未取得（内訳に要確認あり）":"未取得"):a.taxAmount+"円"+(a.taxAmountFromBreakdown?"（内訳合算・要確認）":a.taxAmountUncertain?"（要確認）":""))
            +"\n税込の最終合計："+fiscalValue(a,ReceiptParser.Kind.TOTAL)+"\n照合："+(match==ReceiptFiscal.Match.MATCH?"一致":match==ReceiptFiscal.Match.MISMATCH?"不一致・要確認":relations==ReceiptFiscal.Match.MATCH?"未確定（候補値の金額関係は一致）":relations==ReceiptFiscal.Match.MISMATCH?"未確定（候補値の関係を要確認）":"未確定")
            +(a.amountBasis==ReceiptParser.AmountBasis.INCLUSIVE?"（内税は二重加算しません）":"");}
    String status(){ReceiptParser.Receipt a=aggregate();long sum=0;int q=0;boolean known=true;for(Item i:items){sum+=i.lineAmount;if(i.quantity==null)known=false;else q+=i.quantity;}
        return "商品 "+items.size()+"種類 / 数量 "+(known?q+"点":"未確定あり")+(items.stream().anyMatch(i->i.quantityState==QuantityState.PROVISIONAL_ONE)?"（推定を含む）":"")
            +(a.printedItemCount==null?"":" / 印字点数 "+a.printedItemCount+"点")+(mismatch(items,a)?" / 不一致・要確認":"");}
    void validateSave(){if(batch!=null&&batch.hasDataChanges())throw new IllegalArgumentException("未反映の一括入力があります。反映するか破棄してください");for(Held h:held)if(h.choice==Choice.UNRESOLVED)throw new IllegalArgumentException("集計候補を確認してください");for(Item i:items)if(i.selected&&!i.ready())throw new IllegalArgumentException("推定・未確認の行は保存できません");if(mismatch(items,aggregate())&&!mismatchAccepted)throw new IllegalArgumentException("集計不一致を確認してください");}
    static long printedUnit(Item item){
        if(item.quantity==null||item.quantity<1||item.quantity>999)throw new IllegalArgumentException("数量を確認してください");
        if(item.printedUnit!=null){
            if(item.printedUnit*item.quantity!=item.lineAmount)throw new IllegalArgumentException("値引・単価と行金額を確認してください");
            return item.printedUnit;
        }
        if(item.lineAmount%item.quantity!=0)throw new IllegalArgumentException("割り切れない単価は手入力してください");
        return item.lineAmount/item.quantity;
    }
    /** Call only after a human confirms gross/net, rate and per-unit rounding for the selected rows. */
    static long confirmedUnit(Item item,boolean net,Double tax,Rounding rounding){
        long unit=printedUnit(item);
        if(!net&&item.basis==ReceiptParser.AmountBasis.EXCLUSIVE)throw new IllegalArgumentException("税抜表示です。税込として扱えません");
        if(net&&item.basis==ReceiptParser.AmountBasis.INCLUSIVE)throw new IllegalArgumentException("税込表示です。税を加算できません");
        if(net){
            if(tax==null||!Double.isFinite(tax)||tax<0||tax>100||rounding==null)throw new IllegalArgumentException("税率と単価ごとの端数処理を確認してください");
            java.math.BigDecimal value=java.math.BigDecimal.valueOf(unit).multiply(java.math.BigDecimal.ONE.add(java.math.BigDecimal.valueOf(tax).movePointLeft(2)));
            java.math.RoundingMode mode=rounding==Rounding.DOWN?java.math.RoundingMode.FLOOR:rounding==Rounding.UP?java.math.RoundingMode.CEILING:java.math.RoundingMode.HALF_UP;
            unit=value.setScale(0,mode).longValueExact();
        }
        if(unit<1||unit>1_000_000)throw new IllegalArgumentException("単価が範囲外です");return unit;
    }
}
