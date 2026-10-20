#pragma once
#include "cp_template.h"

#include "fraud_scoring.grpc.pb.h"
#include "scoring_service.h"

namespace fraud {

class FraudScoringServiceImpl final : public FraudScoringService::Service {
  public:
    grpc::Status ScoreTransaction(grpc::ServerContext *context, const ScoringRequest *request,
                                  ScoringResponse *reply) override;
    grpc::Status BatchScore(grpc::ServerContext *context, const BatchScoringRequest *request,
                            BatchScoringResponse *reply) override;
    grpc::Status HealthCheck(grpc::ServerContext *context, const HealthRequest *request,
                             HealthResponse *reply) override;

  private:
    ScoringService scoring_service_;
};

} // namespace fraud
